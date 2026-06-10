import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // 🔥 Pomijanie generowania sourcemap w produkcji (szybszy build)
    sourcemap: false,
    // 🔥 Ostrzeżenia o dużych chunkach
    chunkSizeWarningLimit: 300,
  },
  // 🔥 Serwer deweloperski z szybszym HMR i własnym portem
  server: {
    hmr: true,
    port: 9875,
  },
})
