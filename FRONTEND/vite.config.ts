import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const bffUrl = process.env.VITE_BFF_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4200,
    host: '0.0.0.0',
    allowedHosts: ['pedidos360-alb-571995876.us-east-1.elb.amazonaws.com', '13.220.149.198', 'localhost'],
    proxy: {
      '/api': { target: bffUrl, changeOrigin: true },
      '/oauth2': { target: bffUrl, changeOrigin: true }
    }
  }
})