const NAVIGATION_SELECTORS = '.sidebar, .side-nav, .nav-sidebar, .navbar-top, .top-bar, .topbar, .header-shell';

function normalizePath(path) {
  return path.replace(/\\/$/, '') || '/';
}

function updateActiveLinks(root) {
  const currentPath = normalizePath(window.location.pathname);
  root.querySelectorAll('a[href]').forEach(link => {
    const href = link.getAttribute('href');
    if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;

    let linkPath;
    try {
      linkPath = normalizePath(new URL(href, window.location.href).pathname);
    } catch (_) {
      return;
    }

    const active = linkPath === currentPath;
    if (active) {
      link.classList.add('active');
      link.setAttribute('aria-current', 'page');
    } else if (link.matches('.sidebar-item, .sidebar-link, .nav-link, .nav')) {
      link.classList.remove('active');
      link.removeAttribute('aria-current');
    }
  });
}

function bindLogout(root) {
  root.querySelectorAll('#signOutBtn, #signOutSidebarBtn, [data-action="logout"]').forEach(button => {
    if (button.dataset.logoutBound === 'true') return;
    button.dataset.logoutBound = 'true';
    button.addEventListener('click', event => {
      event.preventDefault();
      if (typeof window.showLogoutConfirmation === 'function' && typeof window.logout === 'function') {
        window.showLogoutConfirmation(window.logout);
      }
    });
  });
}

export function initializeNavigation() {
  if (typeof document === 'undefined') return;
  const roots = document.querySelectorAll(NAVIGATION_SELECTORS);
  if (!roots.length) return;

  roots.forEach(root => {
    root.classList.add('shared-navigation');
    updateActiveLinks(root);
    bindLogout(root);
  });
  document.documentElement.classList.add('shared-navigation-ready');
}
