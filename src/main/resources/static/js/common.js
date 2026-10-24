// Shared helpers used by the pages.
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

/** fetch wrapper: JSON in/out, adds the CSRF header, throws an Error with a readable message. */
async function api(url, { method = 'GET', body } = {}) {
    const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

    const res = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined
    });

    // A session that expired gets redirected to the login page.
    if (res.redirected && res.url.includes('/login')) {
        window.location.href = '/login';
        throw new Error('Your session expired. Please sign in again.');
    }
    if (res.status === 401 || res.status === 403) {
        throw new Error('Not allowed. Reload the page or sign in again.');
    }
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `Request failed (${res.status})`);
    }
    return res.status === 204 ? null : res.json();
}

// Top bar: highlight the current page and open/close the account menu.
document.querySelectorAll('.nav-links a').forEach(a => {
    if (a.pathname === window.location.pathname) a.classList.add('active');
});

const avatarBtn = document.querySelector('.avatar-btn');
const userPop = document.querySelector('.user-pop');
if (avatarBtn && userPop) {
    const setOpen = open => {
        userPop.classList.toggle('hidden', !open);
        avatarBtn.setAttribute('aria-expanded', String(open));
    };
    avatarBtn.addEventListener('click', e => {
        e.stopPropagation();
        setOpen(userPop.classList.contains('hidden'));
    });
    document.addEventListener('click', e => {
        if (!userPop.contains(e.target)) setOpen(false);
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') setOpen(false);
    });
}
