import { apiClient } from './client';

export interface UserResponse {
  id: string;
  email: string;
  full_name: string;
  role: string;
  is_active: boolean;
}

export const authApi = {
  login: async (credentials: URLSearchParams) => {
    // Standard OAuth2 Form Data URL Encoded format required by FastAPI
    const { data } = await apiClient.post('/auth/login', credentials, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
    return data; // returns access_token, refresh_token, token_type
  },

  getMe: async (): Promise<UserResponse> => {
    const { data } = await apiClient.get('/auth/me');
    return data;
  },
};
export default authApi;
