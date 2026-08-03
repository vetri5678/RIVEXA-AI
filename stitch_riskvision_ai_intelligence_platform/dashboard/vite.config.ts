import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  base: '/',
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5176,
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
      '/api/v1/auth': {
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
      '/api/v1/me': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/projects': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // ── FastAPI ML Engine routes — must point to :8000 ────────────────
      '/api/v1/pipeline': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, req, res) => {
            console.error(`[Proxy] /pipeline → FastAPI failed (${err.message})`)
            if (res && 'writeHead' in res) {
              const httpRes = res as any;
              if (!httpRes.headersSent) {
                httpRes.writeHead(503, { 'Content-Type': 'application/json' })
                httpRes.end(JSON.stringify({
                  error: 'FastAPI ML Engine Unavailable',
                  message: `FastAPI unreachable at http://localhost:8000. (${err.message})`,
                  path: req.url,
                  status: 'UNTRAINED',
                  loaded_model: null,
                  reports_count: 0,
                }))
              }
            }
          })
        },
      },
      '/api/v1/health': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/ready': {
        target: process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/profile': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/oauth2': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/login/oauth2': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/dashboard': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/audit': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/telemetry': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/api/v1/ai': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/ws': {
        target: process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
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
            console.error(`[Vite Proxy Error] Failed to proxy ${req.method} ${req.url} to Spring Boot target (${err.message})`)
            if (res && 'writeHead' in res) {
              const httpRes = res as any;
              if (!httpRes.headersSent) {
                httpRes.writeHead(503, { 'Content-Type': 'application/json' })
                httpRes.end(JSON.stringify({
                  error: 'Service Unavailable (ECONNREFUSED)',
                  message: `Backend server unreachable at http://localhost:8080. Please start the backend service. (${err.message})`,
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
