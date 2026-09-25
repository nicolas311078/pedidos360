import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const bffUrl = process.env.VITE_BFF_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4200,
    proxy: {
      '/api': { target: bffUrl, changeOrigin: true },
      '/oauth2': { target: bffUrl, changeOrigin: true }
    }
  }
})