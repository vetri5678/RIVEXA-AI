export interface UserInfo {
  id?: string;
  email?: string;
  username?: string;
  full_name?: string;
  role?: string;
  avatar_url?: string;
  [key: string]: any;
}

/**
 * Retrieve the current authenticated user object from localStorage.
 * If missing, invalid, or corrupted, returns a safe non-admin user object.
 * NEVER defaults to ADMIN or SUPER_ADMIN.
 */
export const getStoredUser = (): UserInfo => {
  try {
    const raw = localStorage.getItem('rv_user') || localStorage.getItem('rivexa_user') || localStorage.getItem('user');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        if (!parsed.email) {
          const token = localStorage.getItem('rv_access_token') || localStorage.getItem('access_token');
          if (token && token.includes('.')) {
            try {
              const payload = JSON.parse(atob(token.split('.')[1]));
              if (payload.sub || payload.email) {
                parsed.email = payload.sub || payload.email;
              }
            } catch {}
          }
        }
        return parsed;
      }
    }
  } catch {}

  // Fallback: try parsing JWT token payload directly
  try {
    const token = localStorage.getItem('rv_access_token') || localStorage.getItem('access_token');
    if (token && token.includes('.')) {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        id: payload.userId || payload.id || payload.sub,
        email: payload.sub || payload.email,
        username: payload.sub?.split('@')[0] || 'user',
        role: payload.role || 'USER',
        full_name: payload.name || 'User',
      };
    }
  } catch {}

  return { role: 'USER', full_name: 'User' };
};

/**
 * Generates the full OAuth authorization URL for GitHub, ensuring user identity parameters are included.
 */
export const getConnectGitHubUrl = (): string => {
  const user = getStoredUser();
  const email = user?.email || '';
  const token = localStorage.getItem('rv_access_token') || localStorage.getItem('access_token') || '';
  const backendUrl = (import.meta as any).env?.VITE_SPRINGBOOT_URL || '';
  const params = new URLSearchParams();
  if (email) params.set('user_email', email);
  if (token) params.set('access_token', token);
  const queryString = params.toString();
  return `${backendUrl}/oauth2/authorization/github${queryString ? `?${queryString}` : ''}`;
};

/**
 * Check if a user has administrative privileges (ADMIN or SUPER_ADMIN).
 * Performs server-assigned role check; returns false for null, USER, or VIEWER.
 */
export const isAdminUser = (user?: UserInfo | null): boolean => {
  const targetUser = user ?? getStoredUser();
  if (!targetUser || !targetUser.role) return false;
  const roleUpper = String(targetUser.role).trim().toUpperCase();
  return roleUpper === 'ADMIN' || roleUpper === 'SUPER_ADMIN';
};

/**
 * Get normalized uppercase role string for UI badge display.
 */
export const getUserRoleDisplay = (user?: UserInfo | null): string => {
  const targetUser = user ?? getStoredUser();
  if (!targetUser || !targetUser.role) return 'USER';
  return String(targetUser.role).trim().toUpperCase();
};
