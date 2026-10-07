// api.js — Central API client for Land Verification System
// Use a relative API base so requests target the same origin serving the frontend.
// This avoids accidental requests to static paths like `api/...` when the host differs.
const BASE_URL = '/api';

function getToken() {
  return localStorage.getItem('lv_token');
}

/* ── Safe JSON parser — never crashes on empty / non-JSON body ── */
async function safeJson(res, label = '') {
  let text;
  try {
    text = await res.text();
  } catch (e) {
    console.error(`[api${label}] Failed to read response body:`, e);
    return {};
  }

  // Always log the raw response so you can see exactly what the server sent
  console.log(`[api${label}] ${res.status} ${res.url}\nBody: ${text || '(empty)'}`);

  if (!text || !text.trim()) return {};

  try {
    return JSON.parse(text);
  } catch {
    // If server sent HTML error page (e.g. 500 Whitelabel Error), extract useful part
    const snippet = text.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 200);
    throw new Error(`Server error: ${snippet || text.slice(0, 120)}`);
  }
}

/* ── Standard JSON request ── */
async function request(method, path, body = null) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = 'Bearer ' + token;

  const opts = { method, headers };
  if (body) opts.body = JSON.stringify(body);

  let res;
  try {
    res = await fetch(BASE_URL + path, opts);
  } catch (networkErr) {
    throw new Error(`Network error — is the server running? (${networkErr.message})`);
  }

  const data = await safeJson(res, ` ${method} ${path}`);

  if (!res.ok) {
    const err = new Error(data.message || data.error || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return data;
}

/* ── Multipart / FormData request (no Content-Type — browser sets boundary) ── */
async function requestMultipart(method, path, formData) {
  const headers = { 'Accept': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = 'Bearer ' + token;
  // ⚠️ Do NOT set Content-Type — browser must set multipart/form-data with boundary

  let res;
  try {
    res = await fetch(BASE_URL + path, { method, headers, body: formData });
  } catch (networkErr) {
    throw new Error(`Network error — is the server running? (${networkErr.message})`);
  }

  const data = await safeJson(res, ` ${method} ${path}`);

  if (!res.ok) {
    throw new Error(data.message || data.error || `Request failed (${res.status})`);
  }
  return data;
}

// ── Auth ──────────────────────────────────────────────
const api = {

  login: (email, password) =>
    request('POST', '/auth/login', { email, password }),

  register: (payload) =>
    request('POST', '/auth/register', payload),

  // ── Public (no login) ─────────────────────────────
  verifyParcel: (parcelNumber) =>
    request('GET', `/public/verify/${encodeURIComponent(parcelNumber)}`),

  searchParcels: (q) =>
    request('GET', `/public/search?q=${encodeURIComponent(q)}`),

  // ── Land Owner ────────────────────────────────────
  landownerDashboard: () =>
    request('GET', '/landowner/dashboard'),

  myParcels: () =>
    request('GET', '/landowner/my-parcels'),

  myTransfers: () =>
    request('GET', '/landowner/my-transfers'),

  initiateTransfer: (payload, files = [], photos = {}) => {
    // Always send multipart/form-data to match backend controller expecting a RequestPart
    const formData = new FormData();
    formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    (files || []).forEach(file => formData.append('files', file));
    const sellerPhoto = photos.sellerPhoto || photos.seller;
    const buyerPhoto = photos.buyerPhoto || photos.buyer;
    if (sellerPhoto) formData.append('sellerPhoto', sellerPhoto);
    if (buyerPhoto) formData.append('buyerPhoto', buyerPhoto);
    return requestMultipart('POST', '/landowner/transfer/initiate', formData);
  },

  myNotifications: () =>
    request('GET', '/landowner/notifications'),

  markNotificationRead: (id) =>
    request('PUT', `/landowner/notifications/${id}/read`),

  unreadNotificationCount: () =>
    request('GET', '/landowner/notifications/count'),

  // ── Land Officer ──────────────────────────────────
  registerParcel: (payload, ownerPhoto = null) => {
    if (!ownerPhoto) return request('POST', '/officer/parcel/register', payload);
    const formData = new FormData();
    formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    formData.append('ownerPhoto', ownerPhoto);
    return requestMultipart('POST', '/officer/parcel/register', formData);
  },

  generateParcelNumber: (province) =>
    request('GET', `/officer/parcel/generate-number?province=${encodeURIComponent(province || '')}`),

  getParcel: (parcelNumber) =>
    request('GET', `/officer/parcel/${encodeURIComponent(parcelNumber)}`),

  getParcelById: (parcelId) =>
    request('GET', `/public/parcel/${encodeURIComponent(parcelId)}`),

  searchLandowners: (q) =>
    request('GET', `/officer/owner/search?q=${encodeURIComponent(q)}`),

  searchOfficerParcels: (q, fromDate, toDate) => {
    const params = new URLSearchParams();
    if (q) params.append('q', q);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    const query = params.toString() ? `?${params.toString()}` : '';
    return request('GET', `/officer/parcels${query}`);
  },

  rejectedParcels: () =>
    request('GET', '/officer/parcels/rejected'),
  approvedParcels: (includePartial = false) =>
    request('GET', `/officer/parcels/approved${includePartial ? '?includePartial=true' : ''}`),

  pendingTransfers: () =>
    request('GET', '/officer/transfers/pending'),

  officerTransfers: (status) =>
    request('GET', `/officer/transfers${status ? '?status=' + status : ''}`),

  dashboardStats: () =>
    request('GET', '/officer/dashboard/stats'),

  officerNotifications: () =>
    request('GET', '/officer/notifications'),

  markOfficerNotificationRead: (id) =>
    request('PUT', `/officer/notifications/${id}/read`),

  officerUnreadNotificationCount: () =>
    request('GET', '/officer/notifications/count'),

  getParcelBlockchainRecord: (parcelId) =>
    request('GET', `/officer/blockchain/parcel/${encodeURIComponent(parcelId)}`),

  seniorNotifications: () =>
    request('GET', '/senior-officer/notifications'),

  markSeniorNotificationRead: (id) =>
    request('PUT', `/senior-officer/notifications/${id}/read`),

  seniorUnreadNotificationCount: () =>
    request('GET', '/senior-officer/notifications/count'),

  // Transfer approval endpoints (blockchain-assisted approval controller)
  transferStatus: (requestId) =>
    request('GET', `/transfers/${encodeURIComponent(requestId)}/status`),

  transferAuditTrail: (requestId) =>
    request('GET', `/transfers/${encodeURIComponent(requestId)}/audit-trail`),

  officerReject: (payload) =>
    request('POST', '/officer/transfer/reject', payload),

  getMyProfile: () =>
    request('GET', '/auth/profile/me'),

  updateProfileDirect: (payload) =>
    request('POST', '/officer/profile/update-direct', payload),

  updateLandownerProfileDirect: (payload) =>
    request('POST', '/landowner/profile/update-direct', payload),

  uploadLandownerProfilePhoto: (formData) =>
    requestMultipart('POST', '/landowner/profile/photo', formData),

  getLandownerPendingChanges: () =>
    request('GET', '/landowner/profile/pending-changes'),

  uploadProfilePhoto: (formData) =>
    requestMultipart('POST', '/officer/profile/photo', formData),

  /* Profile update — sends FormData (name, phone, address, photo, passwords) */
  requestProfileUpdate: (formData) =>
    requestMultipart('POST', '/officer/profile/update', formData),

  /* Fetch pending profile change requests for this officer */
  getMyPendingChanges: () =>
    request('GET', '/officer/profile/pending-changes'),

  /* Change password */
  changePassword: (payload) =>
    request('POST', '/officer/profile/change-password', payload),

  // ── Senior Officer ────────────────────────────────
  // Senior officer endpoints are under /api/senior or /api/senior-officer
  // Use this helper for regular senior officer queries (flagged, status filters, list)
  seniorTransfers: (status) =>
    request('GET', `/senior/transfers${status ? '?status=' + status : ''}`),

  seniorApprove: (payload) =>
    request('POST', '/senior/transfer/approve', payload),

  seniorReject: (payload) =>
    request('POST', '/senior/transfer/reject', payload),

  seniorFlag: (payload) =>
    request('POST', '/senior/transfer/flag', payload),

  finalApprove: (payload) =>
    request('POST', '/senior-officer/transfer/approve', payload),

  auditLog: () =>
    request('GET', '/senior-officer/audit-log'),

  auditLogById: (id) =>
    request('GET', `/senior-officer/audit-log/${encodeURIComponent(id)}`),

  landownerAuditLog: (parcelId) =>
    request('GET', `/landowner/audit-log${parcelId ? '?parcelId=' + encodeURIComponent(parcelId) : ''}`),

  getParcelById: (parcelId) =>
    request('GET', `/public/parcel/${encodeURIComponent(parcelId)}`),

  // ── Admin ─────────────────────────────────────────
  allUsers: () =>
    request('GET', '/admin/users'),

  createUser: (payload) =>
    request('POST', '/admin/users/create', payload),

  createOfficer: (payload) =>
    request('POST', '/admin/officers/create', payload),

  resendOfficerInvitation: (email) =>
    request('POST', `/admin/officers/resend-invitation?email=${encodeURIComponent(email)}`),

  getRecentDocuments: () =>
    request('GET', '/officer/documents/recent'),

  testEmail: (email) =>
    request('POST', `/admin/test-email?testEmail=${encodeURIComponent(email)}`),

  toggleUserActive: (id) =>
    request('PUT', `/admin/users/${id}/toggle-active`),

  changeUserRole: (id, role) =>
    request('PUT', `/admin/users/${id}/role?role=${role}`),

  deleteUser: (id) =>
    request('DELETE', `/admin/users/${id}`),

  /* Officer attachment — for Dashboard documents section */
  attachDocuments: (formData) =>
    requestMultipart('POST', '/officer/documents/attach', formData),

  loadTransfers: () =>
    request('GET', '/officer/transfers/pending'),

  officerApprove: (payload) =>
    request('POST', '/officer/transfer/approve', payload),
};

window.api = api;

export { api, request, requestMultipart };