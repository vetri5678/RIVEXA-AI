import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1/repositories': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to Spring Boot target (${err.message})`)
            if (res && 'writeHead' in res) {
              const httpRes = res as any;
              if (!httpRes.headersSent) {
                httpRes.writeHead(503, { 'Content-Type': 'application/json' })
                httpRes.end(JSON.stringify({
                  error: 'Service Unavailable (ECONNREFUSED)',
                  message: `Spring Boot server unreachable at http://localhost:8080. (${err.message})`,
                  path: req.url,
                }))
              }
            }
          })
        },
      },
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:5000',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to FastAPI target (${err.message})`)
            console.error('Ensure the Python backend server is running on port 5000 (npm run backend).')
            if (res && 'writeHead' in res) {
              const httpRes = res as any;
              if (!httpRes.headersSent) {
                httpRes.writeHead(503, { 'Content-Type': 'application/json' })
                httpRes.end(JSON.stringify({
                  error: 'Service Unavailable (ECONNREFUSED)',
                  message: `Backend server unreachable at http://localhost:5000. Please start the backend service. (${err.message})`,
                  path: req.url,
                }))
              }
            }
          })
        },
      },
    },
  },
})
