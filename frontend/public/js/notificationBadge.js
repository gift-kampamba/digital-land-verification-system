import { api } from './api.js';

const DEFAULT_BADGE_ID = 'notificationsBadge';

export async function refreshNotificationBadge(badgeId = DEFAULT_BADGE_ID) {
  const badge = document.getElementById(badgeId);
  if (!badge) return;

  try {
    const res = await api.unreadNotificationCount();
    const count = Number(res.data ?? 0);

    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : String(count);
      badge.classList.remove('d-none');
      badge.setAttribute('aria-label', `${count} unread notifications`);
    } else {
      badge.classList.add('d-none');
    }
  } catch (e) {
    console.warn('Failed to refresh notification badge', e);
  }
}

export function startNotificationBadgePolling(badgeId = DEFAULT_BADGE_ID, intervalMs = 10000) {
  refreshNotificationBadge(badgeId);
  return setInterval(() => refreshNotificationBadge(badgeId), intervalMs);
}
