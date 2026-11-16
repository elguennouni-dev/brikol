// Push notifications. Android app: the native shell gives its Firebase token (window.BrikolApp).
// Browsers / iPhone PWA: Firebase web messaging with our own service worker (/app/sw.js).
import { api, getSession } from './session';

const KEY = 's3_push_token';
const store = {
  get() { try { return localStorage.getItem(KEY); } catch (e) { return null; } },
  set(v) { try { if (v) localStorage.setItem(KEY, v); else localStorage.removeItem(KEY); } catch (e) { /* ignore */ } }
};

const nativeApp = () => (typeof window !== 'undefined' && window.BrikolApp && typeof window.BrikolApp.pushToken === 'function') ? window.BrikolApp : null;

export const webPushSupported = (cfg) => !!(cfg && cfg.firebase && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window && !nativeApp());
export const webPushState = () => ('Notification' in window ? Notification.permission : 'unsupported');

async function send(token, platform) {
  if (!token || !getSession().token) return;
  store.set(token);
  try { await api('/devices', { method: 'POST', body: { token, platform, lang: getSession().lang } }); } catch (e) { /* retried next start */ }
}

/** After login / at start: registers the device silently when permission was already given. */
export async function syncPush(cfg) {
  const app = nativeApp();
  if (app) {
    const tok = app.pushToken();
    if (tok) await send(tok, 'ANDROID');
    return;
  }
  if (webPushSupported(cfg) && Notification.permission === 'granted') await enableWebPush(cfg, false);
}

/** Called from a button (permission prompts need a user gesture). Returns true when enabled. */
export async function enableWebPush(cfg, ask = true) {
  if (!webPushSupported(cfg)) return false;
  if (ask && Notification.permission !== 'granted') {
    const p = await Notification.requestPermission();
    if (p !== 'granted') return false;
  }
  const [{ initializeApp, getApps }, { getMessaging, getToken }] = await Promise.all([import('firebase/app'), import('firebase/messaging')]);
  const f = cfg.firebase;
  const app = getApps()[0] || initializeApp({ apiKey: f.apiKey, appId: f.appId, projectId: f.projectId, messagingSenderId: f.messagingSenderId });
  const reg = await navigator.serviceWorker.register('/app/sw.js', { scope: '/app/' });
  const token = await getToken(getMessaging(app), { vapidKey: f.vapidKey, serviceWorkerRegistration: reg });
  await send(token, 'WEB');
  return !!token;
}

/** At logout: this device stops receiving the previous user's notifications. */
export async function dropPush() {
  const tok = store.get();
  if (!tok) return;
  store.set(null);
  try { await api('/devices/remove', { method: 'POST', body: { token: tok } }); } catch (e) { /* ignore */ }
}

// The Android shell calls this when Firebase gives it a new token.
if (typeof window !== 'undefined') {
  window.onBrikolPushToken = (tok) => { if (tok) send(tok, 'ANDROID'); };
}
