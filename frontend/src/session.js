import { useEffect, useState, useSyncExternalStore } from 'react';
import { T } from './i18n';

// ------------------------------------------------------------ storage (never throws: private mode, WebView quirks)
const store = {
  get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* ignore */ } }
};

// ------------------------------------------------------------ session store: language, token, user
let user = null;
try { user = JSON.parse(store.get('s3_user') || 'null'); } catch (e) { user = null; }
let snap = { lang: store.get('s3_lang') === 'fr' ? 'fr' : 'ar', token: store.get('s3_token'), user };
const listeners = new Set();
const emit = () => listeners.forEach((f) => f());
const subscribe = (f) => { listeners.add(f); return () => listeners.delete(f); };
export const useSession = () => useSyncExternalStore(subscribe, () => snap);
export const getSession = () => snap;

export function applyLang() {
  document.documentElement.lang = snap.lang;
  document.documentElement.dir = snap.lang === 'ar' ? 'rtl' : 'ltr';
}
export function setLang(lang) {
  snap = { ...snap, lang: lang === 'fr' ? 'fr' : 'ar' };
  store.set('s3_lang', snap.lang);
  applyLang();
  emit();
}
export function setAuth(token, u) {
  snap = { ...snap, token, user: u };
  store.set('s3_token', token);
  store.set('s3_user', u ? JSON.stringify(u) : null);
  emit();
}
export const isAdmin = () => !!snap.user && snap.user.role === 'ADMIN';
/** Administration: the owner can do everything; staff only what their permissions allow. */
export const isOwner = () => isAdmin() && !!snap.user.superAdmin;
export const can = (p) => isAdmin() && (!!snap.user.superAdmin || (snap.user.permissions || []).includes(p));
export const isArtisan = () => !!snap.user && snap.user.role === 'ARTISAN';
/** An artisan works on the platform only once an admin approved his file. */
export const artisanReady = () => isArtisan() && snap.user.artisanStatus === 'APPROVED';
export async function refreshMe() {
  if (!snap.token) return;
  try { const u = await api('/me'); if (u && snap.token) setAuth(snap.token, u); } catch (e) { /* handled in api() */ }
}

// where to go after login
let nextHash = null;
export const setNext = (h) => { nextHash = h; };
export const takeNext = () => { const n = nextHash; nextHash = null; return n; };

// ------------------------------------------------------------ i18n helpers (read the current language from the store)
export const t = (k, v) => {
  let s = (T[snap.lang] && T[snap.lang][k]) || T.fr[k] || k;
  if (v) Object.keys(v).forEach((key) => { s = s.split('{' + key + '}').join(v[key]); });
  return s;
};
export const hasKey = (k) => !!T.fr[k];
export const L = (o, k) => {
  if (!o) return '';
  const ar = o[k + 'Ar'];
  const fr = o[k + 'Fr'];
  return (snap.lang === 'ar' ? (ar || fr) : (fr || ar)) || '';
};
export const fmt = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
export const money = (n) => fmt(n) + ' ' + t('dh');
export const initials = (name) => {
  const p = String(name || '?').trim().split(/\s+/);
  return snap.lang === 'ar' ? p[0].charAt(0) : (p[0].charAt(0) + (p.length > 1 ? p[p.length - 1].charAt(0) : '')).toUpperCase();
};
export const ago = (iso) => {
  const m = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (m < 2) return t('time_now');
  if (m < 60) return t('time_min', { n: m });
  if (m < 60 * 24) return t('time_h', { n: Math.round(m / 60) });
  return t('time_d', { n: Math.round(m / 1440) });
};
export const etaLabel = (x) => (x && hasKey('eta_' + x) ? t('eta_' + x) : (x || ''));
export const errMsg = (e) => {
  const key = 'err_' + ((e && e.code) || 'GENERIC');
  return hasKey(key) ? t(key) : t('err_GENERIC');
};

// ------------------------------------------------------------ toast
const toastListeners = new Set();
export const toast = (msg) => toastListeners.forEach((f) => f(msg));
export function useToast() {
  const [msg, setMsg] = useState('');
  const [show, setShow] = useState(false);
  useEffect(() => {
    let timer;
    const on = (m) => { setMsg(m); setShow(true); clearTimeout(timer); timer = setTimeout(() => setShow(false), 2800); };
    toastListeners.add(on);
    return () => { toastListeners.delete(on); clearTimeout(timer); };
  }, []);
  return { msg, show };
}

// ------------------------------------------------------------ api
export async function api(path, opts) {
  const o = opts || {};
  const headers = {};
  if (o.body !== undefined) headers['Content-Type'] = 'application/json';
  if (snap.token) headers.Authorization = 'Bearer ' + snap.token;
  let res;
  try {
    res = await fetch('/api' + path, { method: o.method || 'GET', headers, body: o.body === undefined ? undefined : JSON.stringify(o.body) });
  } catch (e) {
    throw { code: 'NETWORK' };
  }
  let data = null;
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (res.status === 401 && snap.token && path.indexOf('/auth/') !== 0) {
    setAuth(null, null);
    nextHash = null;
    location.hash = '#/login';
    throw { code: 'SESSION', handled: true };
  }
  if (res.status === 403 && data && data.code === 'ACCOUNT_BLOCKED' && snap.token) {
    setAuth(null, null);
    location.hash = '#/login';
    toast(errMsg({ code: 'ACCOUNT_BLOCKED' }));
    throw { code: 'ACCOUNT_BLOCKED', handled: true };
  }
  if (!res.ok) throw { status: res.status, code: (data && data.code) || 'GENERIC' };
  return data;
}

let catsPromise = null;
let catsList = [];
export const getCats = () => {
  if (!catsPromise) catsPromise = api('/categories').then((c) => { catsList = c; return c; }).catch((e) => { catsPromise = null; throw e; });
  return catsPromise;
};
const catName = (code) => { const c = catsList.find((x) => x.code === code); return c ? L(c, 'name') : (code || ''); };

// Runs a handler and shows errors as a toast.
export async function guarded(fn) {
  try { return await fn(); } catch (e) { if (!(e && e.handled)) toast(errMsg(e)); return undefined; }
}

// ------------------------------------------------------------ data loading hook
// Keeps the previous data while reloading, so the screen does not flash a loader on every refresh.
export function useAsync(fn, deps, opts) {
  const [st, setSt] = useState({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);
  // { live: true } reloads the data whenever a notification arrives (new offer, offer accepted...)
  const liveTick = useSyncExternalStore(nsub, () => nstate.tick);
  useEffect(() => {
    let live = true;
    setSt((s) => ({ ...s, loading: true, error: null }));
    fn().then(
      (data) => { if (live) setSt({ data, error: null, loading: false }); },
      (error) => { if (live && !(error && error.handled)) setSt({ data: null, error, loading: false }); }
    );
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, snap.token, opts && opts.live ? liveTick : 0]);
  return { ...st, reload: () => setTick((x) => x + 1) };
}

// ------------------------------------------------------------ hash router
const hashSub = (f) => { window.addEventListener('hashchange', f); return () => window.removeEventListener('hashchange', f); };
export const useHash = () => useSyncExternalStore(hashSub, () => location.hash);
export function parseRoute(h) {
  const parts = h.replace(/^#\/?/, '').split('?');
  const seg = parts[0].split('/').filter(Boolean);
  return { name: seg[0] || 'home', id: seg[1], sub: seg[2], q: new URLSearchParams(parts[1] || '') };
}
export const back = () => { if (history.length > 1) history.back(); else location.hash = '#/'; };

// ------------------------------------------------------------ notifications (stored + live)
let nstate = { items: [], unread: 0, live: false, tick: 0 };
const nlisteners = new Set();
const nsub = (f) => { nlisteners.add(f); return () => nlisteners.delete(f); };
const nset = (patch) => { nstate = { ...nstate, ...patch }; nlisteners.forEach((f) => f()); };
export const useNotifs = () => useSyncExternalStore(nsub, () => nstate);

export function notifText(n) {
  const name = snap.lang === 'ar' ? (n.actorAr || n.actorFr || '') : (n.actorFr || n.actorAr || '');
  if (n.kind && n.kind.startsWith('APPOINTMENT_') && n.detail) n = { ...n, detail: new Date(n.detail).toLocaleString(snap.lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) };
  const v = (n.kind === 'OFFER_RECEIVED' || n.kind === 'OFFER_COUNTERED' || n.kind === 'OFFER_AGREED') ? money(n.detail) : (n.detail || (n.requestId ? '#' + n.requestId : ''));
  return t('n_' + n.kind, { name, v, cat: catName(n.detail) });
}
export function notifHref(n) {
  if (n.kind === 'ANNOUNCEMENT') return '#/notifications';
  if (n.kind === 'ARTISAN_APPROVED' || n.kind === 'ARTISAN_REJECTED') return '#/onboarding';
  if (n.kind === 'ARTISAN_SUBMITTED') return '#/admin/files';
  if (n.kind === 'REPORT_CREATED') return '#/admin/reports';
  if (n.kind && n.kind.startsWith('APPOINTMENT_') && n.requestId) return '#/chat/' + n.requestId;
  if (n.kind && n.kind.startsWith('SUBSCRIPTION_')) return '#/plan';
  if (n.kind === 'NEW_REQUEST') return '#/jobs';
  return isArtisan() ? '#/jobs?tab=mine' : '#/requests';
}

export async function refreshNotifs() {
  try {
    const p = await api('/notifications');
    if (p) nset({ items: p.items, unread: p.unread });
  } catch (e) { /* offline: the next reconnect refreshes */ }
}
export async function markAllRead() {
  nset({ items: nstate.items.map((n) => ({ ...n, read: true })), unread: 0 });
  try { await api('/notifications/read-all', { method: 'POST' }); } catch (e) { /* ignore */ }
}
export async function markRead(n) {
  if (n.read) return;
  nset({ items: nstate.items.map((x) => (x.id === n.id ? { ...x, read: true } : x)), unread: Math.max(0, nstate.unread - 1) });
  try { await api('/notifications/' + n.id + '/read', { method: 'POST' }); } catch (e) { /* ignore */ }
}
function onLive(n) {
  if (nstate.items.some((x) => x.id === n.id)) return;
  nset({ items: [n, ...nstate.items].slice(0, 50), unread: nstate.unread + 1, tick: nstate.tick + 1 });
  toast(notifText(n));
  if (n.kind === 'ARTISAN_APPROVED' || n.kind === 'ARTISAN_REJECTED') refreshMe();
}

// Live channel: Server-Sent Events read with fetch, so the login token goes in a header (not in the URL).
// Reconnects by itself; after every (re)connection the list is refreshed so nothing is missed.
let liveCtrl = null;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function parseEvent(block) {
  let name = 'message';
  const data = [];
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trim());
  });
  if (!data.length) return;
  try {
    if (name === 'notification') onLive(JSON.parse(data.join('\n')));
    else if (name === 'message') onChat(JSON.parse(data.join('\n')));
  } catch (e) { /* ignore malformed event */ }
}

async function liveLoop(ctrl, token) {
  let delay = 1000;
  while (!ctrl.signal.aborted) {
    try {
      const res = await fetch('/api/notifications/stream', { headers: { Authorization: 'Bearer ' + token, Accept: 'text/event-stream' }, signal: ctrl.signal });
      if (res.status === 401) return;
      if (!res.ok || !res.body) throw new Error('stream');
      delay = 1000;
      nset({ live: true });
      await refreshNotifs();
      nset({ tick: nstate.tick + 1 });
      const reader = res.body.getReader();
      const dec = new TextDecoder();
      let buf = '';
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true }).replace(/\r\n/g, '\n');
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) { parseEvent(buf.slice(0, i)); buf = buf.slice(i + 2); }
      }
    } catch (e) {
      if (ctrl.signal.aborted) return;
    }
    nset({ live: false });
    await sleep(delay);
    delay = Math.min(delay * 2, 15000);
  }
}

// ------------------------------------------------------------ chat messages pushed live
const chatListeners = new Set();
export const onChatMessage = (f) => { chatListeners.add(f); return () => chatListeners.delete(f); };
let openChat = null;                       // request id of the chat on screen (no toast for it)
export const setOpenChat = (id) => { openChat = id; };
function onChat(m) {
  chatListeners.forEach((f) => f(m));
  if (String(openChat) !== String(m.requestId)) {
    toast(m.senderName + ' : ' + (m.body.length > 60 ? m.body.slice(0, 60) + '…' : m.body));
    nset({ tick: nstate.tick + 1 });       // lists with unread counters reload
  }
}

export function startLive() {
  stopLive();
  if (!snap.token) return;
  getCats().catch(() => {});
  liveCtrl = new AbortController();
  liveLoop(liveCtrl, snap.token);
}
export function stopLive() {
  if (liveCtrl) liveCtrl.abort();
  liveCtrl = null;
  nset({ items: [], unread: 0, live: false });
}
// Phones suspend background pages: catch up as soon as the app is visible again.
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && snap.token) { refreshNotifs(); nset({ tick: nstate.tick + 1 }); }
  });
}

// ------------------------------------------------------------ public site config (banner, registrations)
let cfgSnap = { registrationsOpen: true, bannerMessage: '' };
const cfgListeners = new Set();
export const useConfig = () => useSyncExternalStore((f) => { cfgListeners.add(f); return () => cfgListeners.delete(f); }, () => cfgSnap);
export function loadConfig() {
  fetch('/api/public/config').then((r) => (r.ok ? r.json() : null)).then((c) => {
    if (c) { cfgSnap = c; cfgListeners.forEach((f) => f()); }
  }).catch(() => { /* ignore */ });
}
