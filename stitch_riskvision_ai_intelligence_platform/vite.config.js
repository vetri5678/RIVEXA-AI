import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5176,
    // Proxy repository requests to Spring Boot backend, other requests to FastAPI backend
    proxy: {
      '/api/v1/repositories': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to Spring Boot target (${err.message})`);
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({
                error: 'Service Unavailable (ECONNREFUSED)',
                message: `Spring Boot server unreachable at http://localhost:8080. (${err.message})`,
                path: req.url,
              }));
            }
          });
        },
      },
      '/api/v1/auth': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to Spring Boot target (${err.message})`);
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({
                error: 'Service Unavailable (ECONNREFUSED)',
                message: `Spring Boot server unreachable at http://localhost:8080. (${err.message})`,
                path: req.url,
              }));
            }
          });
        },
      },
      '/api/v1/pipeline': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/health': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/dashboard': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/predictions': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/retraining': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to Spring Boot target (${err.message})`);
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({
                error: 'Service Unavailable (ECONNREFUSED)',
                message: `Backend server unreachable at http://localhost:8080. Please start the backend service. (${err.message})`,
                path: req.url,
              }));
            }
          });
        },
      },
    },
  },
});
