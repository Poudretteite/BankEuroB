import axios from 'axios';
import { useAuthStore } from '../store/useAuthStore';

const axiosClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      if (useAuthStore.getState().token) {
        useAuthStore.getState().logout();
      }
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
