import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../auth/authStore';
import { toast } from 'sonner';
import type { ApiErrorBody } from '../types/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Routes that must NOT carry an Authorization header (per WAF agreement).
const PUBLIC_PATHS = ['/auth/login', '/auth/register'];

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const path = config.url ?? '';
  const isPublic = PUBLIC_PATHS.some((p) => path.startsWith(p));
  if (!isPublic) {
    const token = useAuthStore.getState().session?.token;
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
  }
  return config;
});

// Bus event so the router can react to forced logout without coupling axios to react-router.
export const AUTH_EXPIRED_EVENT = 'eventsystem:auth-expired';

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status;
    const body = error.response?.data;
    const fallback = error.message || 'Request failed';
    const message = body?.message ?? fallback;

    if (status === 401) {
      useAuthStore.getState().clear();
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
      toast.error('Your session expired. Please sign in again.');
    } else if (status === 403) {
      toast.error(message || 'You do not have permission to perform this action.');
    } else if (status && status >= 400 && status < 500) {
      toast.error(message);
    } else if (status && status >= 500) {
      toast.error('The server is having trouble. Please try again shortly.');
    } else if (!error.response) {
      toast.error('Network error. Check your connection and try again.');
    }

    return Promise.reject(error);
  },
);

export function extractApiMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ApiErrorBody>(err)) {
    return err.response?.data?.message ?? err.message ?? fallback;
  }
  return fallback;
}
