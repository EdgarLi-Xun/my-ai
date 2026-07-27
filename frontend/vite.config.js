import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// During `npm run dev` the Vite dev server runs at :5173 and proxies
// /api requests to the Spring Boot backend at :8031 (matches
// application.yml `server.port`). `npm run build` outputs the production
// bundle into the Spring Boot static resources directory so the backend
// can serve the UI directly.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8031',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})