import { apiClient } from './client';

export interface UserResponse {
  id: string;
  email: string;
  username: string;
  full_name: string;
  role: string;
  is_active: boolean;
  avatar_url?: string;
  provider?: string;
  login_count?: number;
  last_login?: string;
  created_at?: string;
  connected_accounts?: string[];
}

export const authApi = {
  login: async (credentials: any) => {
    const { data } = await apiClient.post('/auth/login', credentials);
    return data; // returns access_token, refresh_token
  },

  register: async (user: any) => {
    const { data } = await apiClient.post('/auth/register', user);
    return data;
  },

  verifyEmail: async (token: string) => {
    const { data } = await apiClient.get(`/auth/verify-email?token=${encodeURIComponent(token)}`);
    return data;
  },

  resendVerification: async (email: string) => {
    const { data } = await apiClient.post('/auth/resend-verification', { email });
    return data;
  },

  logout: async () => {
    const refreshToken = localStorage.getItem('rv_refresh_token');
    try {
      await apiClient.post('/auth/logout', { refresh_token: refreshToken });
    } catch (e) {
      console.warn('Backend logout request failed', e);
    } finally {
      localStorage.removeItem('rv_access_token');
      localStorage.removeItem('rv_refresh_token');
      localStorage.removeItem('rv_user');
    }
  },

  requestPasswordReset: async (email: string) => {
    const { data } = await apiClient.post('/auth/password-reset', { email });
    return data;
  },

  confirmPasswordReset: async (tokenOrOtp: string, newPassword: string) => {
    const { data } = await apiClient.post('/auth/password-reset/confirm', {
      token: tokenOrOtp,
      otp: tokenOrOtp,
      otpOrToken: tokenOrOtp,
      newPassword: newPassword,
      new_password: newPassword,
    });
    return data;
  },

  changePassword: async (oldPassword: string, newPassword: string) => {
    const { data } = await apiClient.post('/auth/change-password', { oldPassword, newPassword });
    return data;
  },

  getMe: async (): Promise<UserResponse> => {
    const { data } = await apiClient.get('/auth/me');
    return data;
  },

  completeOAuthEmail: async (payload: {
    email: string;
    provider: string;
    providerUserId: string;
    username?: string;
    fullName?: string;
    avatarUrl?: string;
  }) => {
    const { data } = await apiClient.post('/auth/oauth2/complete-email', payload);
    return data; // returns access_token, refresh_token
  },

  getProfile: async (): Promise<UserResponse> => {
    const { data } = await apiClient.get('/profile');
    return data;
  },

  updateProfile: async (payload: { full_name?: string; avatar_url?: string }): Promise<UserResponse> => {
    const { data } = await apiClient.post('/profile/update', payload);
    return data;
  },

  connectAccount: async (payload: {
    provider: string;
    providerUserId: string;
    username?: string;
    fullName?: string;
    avatarUrl?: string;
  }): Promise<UserResponse> => {
    const { data } = await apiClient.post('/profile/connect', payload);
    return data;
  },

  disconnectAccount: async (provider: string): Promise<UserResponse> => {
    const { data } = await apiClient.post('/profile/disconnect', { provider });
    return data;
  },
};

export default authApi;
