import { api } from '../api';

export const authApi = {
  login: async (payload: any) => {
    const response = await api.post('/auth/login', payload);
    return response.data;
  },
  register: async (payload: any) => {
    const response = await api.post('/auth/register', payload);
    return response.data;
  },
  logout: async () => {
    const response = await api.post('/auth/logout', {});
    return response.data;
  },
};
