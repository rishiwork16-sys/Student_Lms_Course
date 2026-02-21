const API_BASE = '/api';

async function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('token');
    const headers = { ...options.headers };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    // Don't set Content-Type if body is FormData (browser sets it with boundary)
    if (!(options.body instanceof FormData)) {
        if (!headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }
    }

    const response = await fetch(`${API_BASE}${url}`, {
        ...options,
        headers
    });

    if (response.status === 403 || response.status === 401) {
        localStorage.removeItem('token');
        const path = window.location.pathname;
        if (path.includes('/admin/')) {
            window.location.href = '/admin/login.html';
        } else {
            window.location.href = '/student/home.html';
        }
    }

    return response;
}

async function fetchWithAuthNoRedirect(url, options = {}) {
    const token = localStorage.getItem('token');
    const headers = { ...options.headers };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    if (!(options.body instanceof FormData)) {
        if (!headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }
    }

    const response = await fetch(`${API_BASE}${url}`, {
        ...options,
        headers
    });

    return response;
}
