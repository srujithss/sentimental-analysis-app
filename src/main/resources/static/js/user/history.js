// History page: loads GET /api/sentiment/history page by page.
(() => {
    const SIZE = 10;
    let page = 0;
    let totalPages = 0;

    const rowsEl = document.getElementById('rows');
    const emptyEl = document.getElementById('empty');
    const emptyFilteredEl = document.getElementById('emptyFiltered');
    const languageEl = document.getElementById('language');
    const errorEl = document.getElementById('error');
    const prevBtn = document.getElementById('prev');
    const nextBtn = document.getElementById('next');
    const infoEl = document.getElementById('pageInfo');

    function cell(text, className) {
        const td = document.createElement('td');
        td.textContent = text;
        if (className) td.className = className;
        return td;
    }

    const LANGUAGE_NAMES = { ta: 'Tamil', ml: 'Malayalam', kn: 'Kannada', te: 'Telugu' };

    function languageName(code) {
        return LANGUAGE_NAMES[code] ?? 'Not identified';
    }

    function pretty(label) {
        return label.charAt(0) + label.slice(1).toLowerCase();
    }

    function addRow(item) {
        const tr = document.createElement('tr');

        tr.appendChild(cell(new Date(item.createdAt).toLocaleString()));

        const shortText = item.text.length > 90 ? item.text.slice(0, 90) + '...' : item.text;
        const textCell = cell(shortText, 'text-cell');
        textCell.title = item.text;
        tr.appendChild(textCell);

        const resultCell = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'label-badge small ' + item.label.toLowerCase();
        badge.textContent = pretty(item.label);
        resultCell.appendChild(badge);
        tr.appendChild(resultCell);

        tr.appendChild(cell(`${(item.confidence * 100).toFixed(1)}%`));
        tr.appendChild(cell(`${languageName(item.languageCode)} / ${item.script}`));

        const notes = [];
        if (item.codeMixed) notes.push('code-mixed');
        if (item.lowConfidence) notes.push('low confidence');
        tr.appendChild(cell(notes.join(', ')));

        rowsEl.appendChild(tr);
    }

    async function load() {
        errorEl.classList.add('hidden');
        try {
            const params = new URLSearchParams({ page, size: SIZE });
            if (languageEl.value) params.set('language', languageEl.value);
            const data = await api(`/api/sentiment/history?${params}`);
            totalPages = data.totalPages;
            rowsEl.replaceChildren();
            data.items.forEach(addRow);
            const empty = data.totalItems === 0;
            emptyEl.classList.toggle('hidden', !empty || languageEl.value !== '');
            emptyFilteredEl.classList.toggle('hidden', !empty || languageEl.value === '');
            infoEl.textContent = data.totalItems === 0 ? '' :
                `Page ${data.page + 1} of ${Math.max(totalPages, 1)} (${data.totalItems} total)`;
            prevBtn.disabled = page <= 0;
            nextBtn.disabled = page + 1 >= totalPages;
        } catch (e) {
            errorEl.textContent = e.message;
            errorEl.classList.remove('hidden');
        }
    }

    languageEl.addEventListener('change', () => { page = 0; load(); });
    prevBtn.addEventListener('click', () => { if (page > 0) { page--; load(); } });
    nextBtn.addEventListener('click', () => { if (page + 1 < totalPages) { page++; load(); } });
    load();
})();
