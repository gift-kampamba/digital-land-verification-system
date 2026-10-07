// auth.js — Session management, role-based redirects
let initializeNavigation = () => {};
const navigationModule = import('/public/js/navigation.js');

function loadNavigationStyles() {
  if (typeof document === 'undefined' || document.querySelector('[data-navigation-styles]')) return;
  const stylesheet = document.createElement('link');
  stylesheet.rel = 'stylesheet';
  stylesheet.href = '/public/css/navigation.css';
  stylesheet.dataset.navigationStyles = 'true';
  document.head.appendChild(stylesheet);
}

loadNavigationStyles();

function disableDocumentTransitionEffects() {
  if (typeof document === 'undefined' || document.querySelector('[data-navigation-stability]')) return;
  const style = document.createElement('style');
  style.dataset.navigationStability = 'true';
  style.textContent = `
    @view-transition { navigation: none; }
    html, body { animation: none !important; transition: none !important; }
    .user-avatar img, #headerPhoto { visibility: hidden; }
    .user-avatar img.profile-photo-ready, #headerPhoto.profile-photo-ready { visibility: visible; }
  `;
  document.head.appendChild(style);
}

disableDocumentTransitionEffects();

export function getPhotoUrl(photoPath) {
  if (!photoPath) return '';
  if (/^(https?:|data:|blob:)/i.test(photoPath)) return photoPath;
  const normalizedPath = String(photoPath).replace(/\\/g, '/').replace(/^\/+/, '');
  return `/${normalizedPath.startsWith('uploads/') ? normalizedPath : `uploads/${normalizedPath}`}`;
}

export function saveSession(data) {
  localStorage.setItem('lv_token',    data.token);
  localStorage.setItem('lv_email',    data.email);
  localStorage.setItem('lv_name',     data.fullName);
  localStorage.setItem('lv_fullName', data.fullName);
  localStorage.setItem('lv_role',     data.role);
  localStorage.setItem('lv_userId',   data.userId);
  localStorage.setItem('lv_phone',    data.phoneNumber || '');
  localStorage.setItem('lv_address',  data.address || '');
  localStorage.setItem('lv_photo',    getPhotoUrl(data.photoPath));
  localStorage.setItem('lv_gender',   data.gender || '');
  localStorage.setItem('lv_profile_complete', data.profileComplete);
  localStorage.setItem('lv_username', data.username || '');
  localStorage.setItem('lv_district', data.district || '');
  localStorage.setItem('lv_national_id', data.nationalId || '');
}

export function clearSession() {
  const keys = ['lv_token','lv_email','lv_name','lv_fullName','lv_role','lv_userId',
                'lv_phone','lv_address','lv_photo','lv_gender','lv_profile_complete',
                'lv_username','lv_district','lv_national_id'];
  keys.forEach(k => localStorage.removeItem(k));
}

export function getSession() {
  const displayName = localStorage.getItem('lv_fullName') || localStorage.getItem('lv_name');
  return {
    token:  localStorage.getItem('lv_token'),
    email:  localStorage.getItem('lv_email'),
    fullName: displayName,
    name:   displayName,
    role:   localStorage.getItem('lv_role'),
    userId: localStorage.getItem('lv_userId'),
    phoneNumber: localStorage.getItem('lv_phone'),
    phone:  localStorage.getItem('lv_phone'),
    address: localStorage.getItem('lv_address'),
    photoPath: getPhotoUrl(localStorage.getItem('lv_photo')),
    gender: localStorage.getItem('lv_gender'),
    profileComplete: localStorage.getItem('lv_profile_complete') === 'true',
    username: localStorage.getItem('lv_username'),
    district: localStorage.getItem('lv_district'),
    nationalId: localStorage.getItem('lv_national_id'),
  };
}

function hydrateCachedProfileVisuals() {
  if (typeof document === 'undefined') return;
  const session = getSession();
  const displayName = session.fullName || session.name || session.email || '';
  const initials = displayName.split(/\s+/).filter(Boolean).map(part => part[0]).join('').slice(0, 2).toUpperCase();
  const avatarElements = document.querySelectorAll('#userAvatarInitials, #nav-avatar, #navAvatar, #nav-avatar-initials, #headerInitials, #bannerAvatarInitial, #heroAvatarCircle');
  const nameElements = document.querySelectorAll('#topUserName, #nav-name, #userName');
  const roleElements = document.querySelectorAll('#topUserDetails, #nav-role');
  const photoUrl = session.photoPath || '';

  nameElements.forEach(element => {
    if (displayName) element.textContent = displayName;
  });
  roleElements.forEach(element => {
    if (session.role) element.textContent = session.role.replace(/_/g, ' ');
  });

  avatarElements.forEach(avatar => {
    const initialsElement = avatar.querySelector('[aria-label="Current user"]') || avatar.querySelector('span');
    if (photoUrl) {
      const image = avatar.querySelector('img') || document.createElement('img');
      image.src = photoUrl;
      image.alt = 'Profile photo';
      image.classList.remove('profile-photo-ready');
      image.onload = () => {
        image.classList.add('profile-photo-ready');
        if (initialsElement) initialsElement.style.display = 'none';
      };
      image.onerror = () => {
        localStorage.removeItem('lv_photo');
        image.remove();
        if (initials) avatar.textContent = initials;
      };
      if (!image.parentElement) avatar.appendChild(image);
      if (image.complete && image.naturalWidth > 0) image.onload();
    } else if (initials && !avatar.querySelector('img')) {
      avatar.textContent = initials;
    }
  });
}

function initializePersistentNavigation() {
  if (typeof document === 'undefined') return;

  initializeNavigation();
  const navigation = document.querySelector('.sidebar, .navbar-top');
  if (!navigation) return;

  const currentPath = window.location.pathname.replace(/\/$/, '') || '/';
  navigation.querySelectorAll('a[href]').forEach(link => {
    const href = link.getAttribute('href');
    if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;

    let linkPath;
    try {
      linkPath = new URL(href, window.location.href).pathname.replace(/\/$/, '') || '/';
    } catch (_) {
      return;
    }

    if (linkPath === currentPath) {
      link.classList.add('active');
      link.setAttribute('aria-current', 'page');
    } else if (link.matches('.sidebar-item, .nav-link, .nav')) {
      link.classList.remove('active');
      link.removeAttribute('aria-current');
    }
  });
}

navigationModule
  .then(({ initializeNavigation: initialize }) => {
    initializeNavigation = initialize;
    initializePersistentNavigation();
  })
  .catch(() => {});

if (typeof window !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializePersistentNavigation, { once: true });
  } else {
    initializePersistentNavigation();
  }
}

function isTempToken(token) {
  return typeof token === 'string' && token.startsWith('temp-');
}

export function isLoggedIn() {
  const token = localStorage.getItem('lv_token');
  return !!token && !isTempToken(token);
}

export function requireLogin() {
  const token = localStorage.getItem('lv_token');
  if (!token || isTempToken(token)) {
    clearSession();
    window.location.href = '/login.html';
  }
}

export function redirectByRole() {
  const role = localStorage.getItem('lv_role');
  const map = {
    LAND_OWNER:     '/pages/landowner/dashboard.html',
    LAND_OFFICER:    '/pages/officer/dashboard.html',
    SENIOR_OFFICER:  '/pages/senior-officer/dashboard.html',
    SYSTEM_ADMIN:    '/pages/admin/dashboard.html',
    PUBLIC_VERIFIER: '/index.html',
  };
  window.location.href = map[role] || '/index.html';
}

let logoutInProgress = false;

export function logout() {
  if (logoutInProgress) return;
  logoutInProgress = true;
  clearSession();
  window.location.replace('/login.html');
}

/**
 * Show a professional logout confirmation dialog
 * @param {Function} callback - Function to call if user confirms logout
 */
export function showLogoutConfirmation(callback) {
  const session = getSession();
  const userRole = session?.role || 'User';
  const userName = session?.fullName || 'User';

  // Create overlay
  const overlay = document.createElement('div');
  overlay.id = 'logout-confirm-overlay';
  overlay.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9999;
    font-family: 'DM Sans', 'Segoe UI', sans-serif;
  `;

  const dialog = document.createElement('div');
  dialog.style.cssText = `
    background: white;
    border-radius: 12px;
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
    max-width: 420px;
    padding: 32px;
    text-align: center;
  `;

  const icon = document.createElement('div');
  icon.style.cssText = `
    font-size: 48px;
    margin-bottom: 16px;
    color: #0e2240;
  `;
  icon.textContent = '🔐';

  const title = document.createElement('h2');
  title.style.cssText = `
    font-size: 18px;
    font-weight: 600;
    color: #0e2240;
    margin: 0 0 8px 0;
  `;
  title.textContent = 'Sign Out of ARMS?';

  const message = document.createElement('p');
  message.style.cssText = `
    font-size: 14px;
    color: #4b5568;
    margin: 0 0 24px 0;
    line-height: 1.5;
  `;
  message.innerHTML = `You are currently signed in as <strong>${userName}</strong>, ${ROLE_LABELS[userRole] || userRole}.<br>Your current session will end, and you will be redirected to the login page.`;

  const warning = document.createElement('div');
  warning.style.cssText = `
    background: #fef3c7;
    border: 1px solid #fcd34d;
    border-radius: 6px;
    padding: 12px;
    margin-bottom: 24px;
    font-size: 13px;
    color: #92400e;
    display: flex;
    align-items: flex-start;
    gap: 8px;
  `;
  warning.innerHTML = `<span style="font-size: 16px; flex-shrink: 0;">⚠️</span><strong>Unsaved changes will be lost. Please save or complete any pending work before signing out.</strong>`;

  const buttonContainer = document.createElement('div');
  buttonContainer.style.cssText = `
    display: flex;
    gap: 12px;
    justify-content: center;
  `;

  const cancelBtn = document.createElement('button');
  cancelBtn.style.cssText = `
    padding: 10px 24px;
    border: 1.5px solid #d6ceba;
    background: white;
    color: #0e2240;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
    font-family: inherit;
  `;
  cancelBtn.textContent = 'Cancel';
  cancelBtn.onmouseover = () => { cancelBtn.style.background = '#faf7f2'; };
  cancelBtn.onmouseout = () => { cancelBtn.style.background = 'white'; };
  cancelBtn.onclick = () => overlay.remove();

  const confirmBtn = document.createElement('button');
  confirmBtn.style.cssText = `
    padding: 10px 24px;
    border: none;
    background: #9a7420;
    color: white;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
    font-family: inherit;
  `;
  confirmBtn.textContent = 'Sign Out';
  confirmBtn.onmouseover = () => { confirmBtn.style.background = '#8a6a1f'; };
  confirmBtn.onmouseout = () => { confirmBtn.style.background = '#9a7420'; };
  confirmBtn.onclick = () => {
    overlay.remove();
    if (callback && typeof callback === 'function') {
      callback();
    }
  };

  buttonContainer.appendChild(cancelBtn);
  buttonContainer.appendChild(confirmBtn);

  dialog.appendChild(icon);
  dialog.appendChild(title);
  dialog.appendChild(message);
  dialog.appendChild(warning);
  dialog.appendChild(buttonContainer);
  overlay.appendChild(dialog);
  document.body.appendChild(overlay);

  // Close on Escape
  const closeOnEscape = (e) => {
    if (e.key === 'Escape') {
      overlay.remove();
      document.removeEventListener('keydown', closeOnEscape);
    }
  };
  document.addEventListener('keydown', closeOnEscape);
}

if (typeof window !== 'undefined') {
  window.logout = logout;
  window.doLogout = logout;
  window.showLogoutConfirmation = showLogoutConfirmation;
  window.confirmAndLogout = function(e) {
    if (e && typeof e.preventDefault === 'function') {
      e.preventDefault();
    }
    showLogoutConfirmation(logout);
  };
  window.requireLogin = requireLogin;
  window.populateNavbar = populateNavbar;
  // Expose session helpers for non-module pages (e.g. login.html)
  window.saveSession = saveSession;
  window.clearSession = clearSession;
  window.getSession = getSession;
  window.getToken = getToken;
  window.redirectByRole = redirectByRole;
}

if (typeof document !== 'undefined') {
  if (document.querySelector('#userAvatarInitials, #nav-avatar, #navAvatar, #nav-avatar-initials, #headerInitials, #bannerAvatarInitial, #heroAvatarCircle')) {
    hydrateCachedProfileVisuals();
  } else if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', hydrateCachedProfileVisuals, { once: true });
  } else {
    hydrateCachedProfileVisuals();
  }
}

const ROLE_LABELS = {
  LAND_OWNER:     'Land Owner',
  LAND_OFFICER:   'Land Officer',
  SENIOR_OFFICER: 'Senior Officer', 
  SYSTEM_ADMIN:   'System Administrator',
  PUBLIC_VERIFIER:'Public Verifier',
};

export function populateNavbar() {
  const s = getSession();
  const nameEl = document.getElementById('nav-name');
  const roleEl = document.getElementById('nav-role');
  if (nameEl) nameEl.textContent = s.fullName || s.name || s.email || 'User';
  if (roleEl) roleEl.textContent = ROLE_LABELS[s.role] || (s.role || '').replace(/_/g, ' ');

  const avatarEl = document.getElementById('nav-avatar') || document.getElementById('navAvatar');
  const savedPhoto = s.photoPath || localStorage.getItem('lv_photo') || '';
  if (avatarEl && savedPhoto) {
    const photoUrl = savedPhoto.startsWith('http') || savedPhoto.startsWith('/') ? savedPhoto : `/uploads/${savedPhoto}`;
    avatarEl.innerHTML = `<img src="${photoUrl}" alt="Profile photo" />`;
  }
}

export function updateSession(updatedData) {
  if (updatedData.fullName) {
    localStorage.setItem('lv_name', updatedData.fullName);
    localStorage.setItem('lv_fullName', updatedData.fullName);
  }
  if (updatedData.phoneNumber) {
    localStorage.setItem('lv_phone', updatedData.phoneNumber);
  }
  if (updatedData.address) {
    localStorage.setItem('lv_address', updatedData.address);
  }
  if (updatedData.gender) {
    localStorage.setItem('lv_gender', updatedData.gender);
  }
  if (updatedData.photoPath) {
    localStorage.setItem('lv_photo', getPhotoUrl(updatedData.photoPath));
  }
  if (updatedData.username) {
    localStorage.setItem('lv_username', updatedData.username);
  }
  if (updatedData.district) {
    localStorage.setItem('lv_district', updatedData.district);
  }
  if (updatedData.nationalId) {
    localStorage.setItem('lv_national_id', updatedData.nationalId);
  }
}

export function getToken() {
  return localStorage.getItem('lv_token');
}

/**
 * ═══════════════════════════════════════════════════════════════════════
 * ROLE-BASED ACCESS CONTROL (RBAC) — Enhanced Security
 * ═══════════════════════════════════════════════════════════════════════
 */

/**
 * Verify that the current user has the required role.
 * If not, redirect to login with a message.
 * 
 * @param {string|string[]} allowedRoles - Role(s) that are allowed
 * @param {string} redirectUrl - URL to redirect to if access denied (default: /login.html)
 * @returns {boolean} true if user has required role, false otherwise
 */
export function requireRole(allowedRoles, redirectUrl = '/login.html') {
  const userRole = localStorage.getItem('lv_role');
  const rolesArray = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
  
  if (!userRole || !rolesArray.includes(userRole)) {
    console.warn(`[RBAC] Access denied. User role '${userRole}' not in allowed roles:`, rolesArray);
    clearSession();
    
    // Show a more informative message by redirecting with an error parameter
    if (userRole) {
      // User tried to access a dashboard they don't have permission for
      window.location.href = redirectUrl + '?error=invalid_role&role=' + encodeURIComponent(userRole);
    } else {
      // No user logged in
      window.location.href = redirectUrl;
    }
    return false;
  }
  return true;
}

/**
 * Check if the current user has a specific role (non-blocking).
 * Returns boolean without redirecting.
 * 
 * @param {string|string[]} requiredRoles - Role(s) to check for
 * @returns {boolean} true if user has the role, false otherwise
 */
export function hasRole(requiredRoles) {
  const userRole = localStorage.getItem('lv_role');
  const rolesArray = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
  return !!userRole && rolesArray.includes(userRole);
}

/**
 * Enforce role-based access for a specific page.
 * Call this at the top of each dashboard page to prevent unauthorized access.
 * 
 * @param {string|string[]} allowedRoles - Role(s) that can access this page
 * @param {string} pageName - Name of the page (for logging)
 */
export function enforceRoleAccess(allowedRoles, pageName = 'Page') {
  // First check if user is logged in
  if (!isLoggedIn()) {
    console.warn(`[RBAC] ${pageName}: User not logged in. Redirecting to login.`);
    window.location.href = '/login.html';
    return;
  }
  
  // Then check if user has the required role
  if (!requireRole(allowedRoles)) {
    const userRole = localStorage.getItem('lv_role');
    console.error(`[RBAC] ${pageName}: Access denied for role '${userRole}'.`);
    // requireRole already handles the redirect
    return;
  }
  
  console.log(`[RBAC] ${pageName}: Access granted for role '${localStorage.getItem('lv_role')}'.`);
}

/**
 * Prevent cross-role access by validating the user's role against the current page.
 * Use this in each dashboard to ensure users can only access their own role's dashboard.
 */
export const RolePageMap = {
  'LAND_OWNER': '/pages/landowner/dashboard.html',
  'LAND_OFFICER': '/pages/officer/dashboard.html',
  'SENIOR_OFFICER': '/pages/senior-officer/dashboard.html',
  'SYSTEM_ADMIN': '/pages/admin/dashboard.html'
};

export function validatePageAccess(currentPagePath) {
  const userRole = localStorage.getItem('lv_role');
  if (!userRole) {
    window.location.href = '/login.html';
    return false;
  }
  
  const allowedPage = RolePageMap[userRole];
  // Normalize paths for comparison
  const normalizedCurrent = currentPagePath.toLowerCase().replace(/\\/g, '/');
  const normalizedAllowed = (allowedPage || '').toLowerCase().replace(/\\/g, '/');
  
  if (!normalizedCurrent.includes(normalizedAllowed.split('/').pop())) {
    console.error(`[RBAC] Cross-role access attempt detected!`);
    console.error(`  User role: ${userRole}`);
    console.error(`  Allowed page: ${allowedPage}`);
    console.error(`  Attempted page: ${currentPagePath}`);
    
    // Redirect to the user's correct dashboard
    window.location.href = allowedPage;
    return false;
  }
  
  return true;
}