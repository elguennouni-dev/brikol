# Web app (React + Vite)

Source of the web app served at `/app/`. The production build is written into
`../backend/src/main/resources/static/app` (already built and included, so the server needs no Node).

```
npm install
npm run dev      # http://localhost:5173/app/  (proxies /api to http://localhost:8080)
npm run build    # rebuilds backend/src/main/resources/static/app  -> then rebuild/redeploy the backend
```
Structure: `src/session.js` (language, login, api, router, data hook), `src/ui.jsx` (shared components, loader),
`src/views/*` (one file per screen), `src/i18n.js` (Darija + French texts), `src/loader.css` (splash + loader animations).
