import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The production build is written straight into the Spring Boot static folder (served at /app/).
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static/app',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }   // npm run dev + mvn spring-boot:run
  }
});
