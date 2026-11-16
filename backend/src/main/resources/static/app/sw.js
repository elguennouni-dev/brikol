// Minimal service worker: makes the app installable (PWA) and keeps the shell available offline.
// API calls are never cached. Hashed files in /app/assets/ never change, so they are served cache-first.
const CACHE = 'brikol-shell-v4';
self.addEventListener('install', (e) => { self.skipWaiting(); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin || !u.pathname.startsWith('/app/')) return;
  if (u.pathname.startsWith('/app/assets/')) {
    e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request).then((r) => {
      const copy = r.clone();
      caches.open(CACHE).then((c) => c.put(e.request, copy));
      return r;
    })));
    return;
  }
  e.respondWith(fetch(e.request).then((r) => {
    const copy = r.clone();
    caches.open(CACHE).then((c) => c.put(e.request, copy));
    return r;
  }).catch(() => caches.match(e.request).then((hit) => hit || caches.match('/app/'))));
});

// ------------------------------------------------------------ push notifications (Firebase Cloud Messaging)
self.addEventListener('push', (e) => {
  let p = {};
  try { p = e.data ? e.data.json() : {}; } catch (err) { p = { notification: { body: e.data ? e.data.text() : '' } }; }
  const n = p.notification || {};
  const d = p.data || {};
  const link = (p.fcmOptions && p.fcmOptions.link) || d.link || '/app/';
  e.waitUntil(self.registration.showNotification(n.title || d.title || 'Brikol', {
    body: n.body || d.body || '', icon: '/app/icons/icon-192.png', badge: '/app/brand/favicon.png', data: { link }, tag: d.tag || undefined
  }));
});
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const link = (e.notification.data && e.notification.data.link) || '/app/';
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    for (const c of list) { if (c.url.includes('/app/') && 'focus' in c) { c.navigate(link); return c.focus(); } }
    return self.clients.openWindow(link);
  }));
});
