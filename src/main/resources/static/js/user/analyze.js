// Analyze page: sends the text to POST /api/sentiment and shows the result.
// Uses api() from common.js (adds the CSRF header and handles errors).
(() => {
    const MAX = 1000;
    const textEl = document.getElementById('text');
    const counterEl = document.getElementById('counter');
    const btn = document.getElementById('analyzeBtn');
    const errorEl = document.getElementById('error');
    const resultEl = document.getElementById('result');
    const labelEl = document.getElementById('label');
    const confidenceEl = document.getElementById('confidence');
    const barsEl = document.getElementById('bars');
    const langEl = document.getElementById('lang');
    const codeMixEl = document.getElementById('codemix');
    const lowConfEl = document.getElementById('lowconf');
    const latencyEl = document.getElementById('latency');
    const languageEl = document.getElementById('language');

    const translitBoxEl = document.getElementById('translit-box');
    const translitTextEl = document.getElementById('translit-text');

    const LABELS = ['NEGATIVE', 'NEUTRAL', 'POSITIVE'];
    const LANGUAGE_KEY = 'analyze.language';

    // Remember the picked language for the next visit (storage can be unavailable, e.g. private mode).
    try {
        const saved = localStorage.getItem(LANGUAGE_KEY);
        if (saved && [...languageEl.options].some((o) => o.value === saved)) languageEl.value = saved;
    } catch (e) { /* keep auto-detect */ }
    languageEl.addEventListener('change', () => {
        try { localStorage.setItem(LANGUAGE_KEY, languageEl.value); } catch (e) { /* not essential */ }
    });

    function updateCounter() {
        counterEl.textContent = `${textEl.value.length} / ${MAX}`;
    }

    function showError(message) {
        errorEl.textContent = message;          // textContent: never interprets HTML
        errorEl.classList.remove('hidden');
    }

    function hideError() {
        errorEl.classList.add('hidden');
    }

    function setBusy(busy) {
        btn.disabled = busy;
        btn.textContent = busy ? 'Analyzing...' : 'Analyze';
    }

    function pretty(label) {
        return label.charAt(0) + label.slice(1).toLowerCase();
    }

    function render(r) {
        labelEl.textContent = pretty(r.label);
        labelEl.className = 'label-badge ' + r.label.toLowerCase();
        confidenceEl.textContent = `${(r.confidence * 100).toFixed(1)}% confidence`;

        barsEl.replaceChildren();
        for (const name of LABELS) {
            const p = r.probabilities[name] ?? 0;
            const row = document.createElement('div');
            row.className = 'bar-row';

            const caption = document.createElement('span');
            caption.className = 'bar-label';
            caption.textContent = pretty(name);

            const track = document.createElement('div');
            track.className = 'bar-track';
            const fill = document.createElement('div');
            fill.className = 'bar-fill ' + name.toLowerCase();
            fill.style.width = `${(p * 100).toFixed(1)}%`;
            track.appendChild(fill);

            const value = document.createElement('span');
            value.className = 'bar-value';
            value.textContent = `${(p * 100).toFixed(1)}%`;

            row.append(caption, track, value);
            barsEl.appendChild(row);
        }

        if (r.transliteratedText) {
            translitTextEl.textContent = r.transliteratedText;
            translitBoxEl.classList.remove('hidden');
        } else {
            translitBoxEl.classList.add('hidden');
        }

        langEl.textContent = `${r.languageName} (${r.script} script)`;
        codeMixEl.classList.toggle('hidden', !r.codeMixed);
        lowConfEl.classList.toggle('hidden', !r.lowConfidence);
        latencyEl.textContent = `${r.latencyMs} ms`;
        resultEl.classList.remove('hidden');
    }

    async function run() {
        const text = textEl.value.trim();
        hideError();
        if (!text) {
            resultEl.classList.add('hidden');
            showError('Please enter some text.');
            return;
        }
        setBusy(true);
        try {
            const language = languageEl.value || undefined;   // undefined is left out of the JSON: auto-detect
            const result = await api('/api/sentiment', { method: 'POST', body: { text, language } });
            render(result);
        } catch (e) {
            resultEl.classList.add('hidden');
            showError(e.message);
        } finally {
            setBusy(false);
        }
    }

    textEl.addEventListener('input', updateCounter);
    textEl.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') run();
    });
    btn.addEventListener('click', run);
    document.querySelectorAll('.chip-btn').forEach((b) => {
        b.addEventListener('click', () => {
            textEl.value = b.dataset.text;
            if (b.dataset.lang) languageEl.value = b.dataset.lang;
            updateCounter();
            textEl.focus();
        });
    });
    updateCounter();
})();
