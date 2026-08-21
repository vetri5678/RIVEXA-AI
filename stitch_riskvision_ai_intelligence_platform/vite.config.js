import { defineConfig } from 'vite';

const vitePort = parseInt(process.env.VITE_PORT, 10) || 5176;

export default defineConfig({
  server: {
    host: '127.0.0.1',
    port: vitePort,
    strictPort: false,
    proxy: {
      '/api/v1/pipeline': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      },
      '/oauth2': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
