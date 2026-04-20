import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Theme = 'dark' | 'light';

interface ThemeState {
  theme: Theme;
  toggleTheme: () => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: 'dark', // domyślny motyw
      toggleTheme: () => {
        const currentTheme = get().theme;
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        set({ theme: newTheme });
        document.documentElement.setAttribute('data-theme', newTheme);
      },
    }),
    {
      name: 'bankeurob-theme',
      onRehydrateStorage: () => (state) => {
        // Po załadowaniu strony natychmiast aplikuj zapisany motyw
        if (state) {
          document.documentElement.setAttribute('data-theme', state.theme);
        }
      },
    }
  )
);

// Fallback dla pre-renederingu
if (typeof document !== 'undefined') {
  const storedThemeConfig = localStorage.getItem('bankeurob-theme');
  if (storedThemeConfig) {
    try {
      const parsed = JSON.parse(storedThemeConfig);
      document.documentElement.setAttribute('data-theme', parsed.state?.theme || 'dark');
    } catch {}
  }
}
