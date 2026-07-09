import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    // Proxy /api requests to the FastAPI backend on :8000
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
