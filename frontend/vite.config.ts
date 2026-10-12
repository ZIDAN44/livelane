/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Same-origin in dev: the refresh-token cookie stays first-party and no CORS is needed.
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/ws': { target: backend, ws: true, changeOrigin: true },
      '/swagger-ui': { target: backend, changeOrigin: true },
      '/v3/api-docs': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
  },
})
