import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
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
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:5000',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to FastAPI target (${err.message})`);
            console.error('Ensure the Python backend server is running on port 5000 (npm run backend).');
            if (res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({
                error: 'Service Unavailable (ECONNREFUSED)',
                message: `Backend server unreachable at http://localhost:5000. Please start the backend service. (${err.message})`,
                path: req.url,
              }));
            }
          });
        },
      },
    },
  },
});
