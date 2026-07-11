import { mlApiClient } from './client';

export interface UserResponse {
  id: string;
  email: string;
  full_name: string;
  role: string;
  is_active: boolean;
}

export const authApi = {
  login: async (credentials: any) => {
    const { data } = await mlApiClient.post('/auth/login', credentials);
    return data; // returns access_token, refresh_token, token_type
  },

  getMe: async (): Promise<UserResponse> => {
    const { data } = await mlApiClient.get('/auth/me');
    return data;
  },
};
export default authApi;
