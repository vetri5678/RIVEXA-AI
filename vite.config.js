import { defineConfig } from 'vite';

const vitePort = parseInt(process.env.VITE_PORT, 10) || 5176;

export default defineConfig({
  root: 'stitch_riskvision_ai_intelligence_platform',
  server: {
    host: '127.0.0.1',
    port: vitePort,
    strictPort: false,
    proxy: {
      // ── FastAPI ML Engine (:8000) routes ──────────────────────────────────
      '/api/v1/pipeline/predict': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/train': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/reports': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/model': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/status': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/metrics': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/pipeline/evaluation': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/health': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/ready': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/retraining': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/models': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },

      // ── Spring Boot (:8080) pipeline routes ──────────────────────────────
      '/api/v1/pipeline': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Proxy] /pipeline → Spring Boot failed (${err.message})`);
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({
                error: 'Spring Boot Pipeline Service Unavailable',
                message: `Spring Boot unreachable at http://127.0.0.1:8080. (${err.message})`,
                path: req.url,
              }));
            }
          });
        },
      },

      // ── Spring Boot (:8080) routes ────────────────────────────────────────
      '/api': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Proxy] ${req.url} → Spring Boot failed (${err.message})`);
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: 'Spring Boot unavailable', message: err.message, path: req.url }));
            }
          });
        },
      },

      '/oauth2': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      },
      '/login/oauth2': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
