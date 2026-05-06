import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface User {
  customerId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  _hasBlikPin: boolean;
  setAuth: (user: User, token: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  setHasBlikPin: (value: boolean) => void;
  getHasBlikPin: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      _hasBlikPin: false,
      setAuth: (user, token) => set({ user, token }),
      logout: () => set({ user: null, token: null }),
      isAuthenticated: () => !!get().token,
      setHasBlikPin: (value) => set({ _hasBlikPin: value }),
      getHasBlikPin: () => get()._hasBlikPin,
    }),
    {
      name: 'bankeurob-auth-storage',
    }
  )
);
