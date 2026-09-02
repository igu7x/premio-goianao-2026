import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Configuracao separada da do Vite para nao carregar o proxy de dev nos testes.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/teste/preparo.ts'],
    css: false,
  },
})
