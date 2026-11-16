import { useEffect, useRef, useState } from 'react';
import { useConfig, useSession } from './session';

// Google blocks its sign-in page inside app WebViews ("disallowed_useragent"): hide the button there.
export const inAppWebView = () => /Sanai3iApp|; wv\)/.test(navigator.userAgent || '');

let gisPromise = null;
function loadGis() {
  if (window.google && window.google.accounts && window.google.accounts.id) return Promise.resolve();
  if (!gisPromise) {
    gisPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://accounts.google.com/gsi/client';
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => { gisPromise = null; reject(new Error('gis')); };
      document.head.appendChild(s);
    });
  }
  return gisPromise;
}

// Reads name / e-mail from the Google ID token (display only: the server verifies the signature).
export function tokenProfile(credential) {
  try {
    const part = credential.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(atob(part).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
    const p = JSON.parse(json);
    return { name: p.name || '', email: p.email || '', picture: p.picture || '' };
  } catch (e) {
    return { name: '', email: '', picture: '' };
  }
}

/** Official "Sign in with Google" button. onCredential(idToken) is called after the user picks an account. */
export function GoogleButton({ onCredential, signup = false }) {
  const { googleClientId } = useConfig();
  const { lang } = useSession();
  const ref = useRef(null);
  const cb = useRef(onCredential);
  cb.current = onCredential;
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!googleClientId || inAppWebView()) return undefined;
    let alive = true;
    loadGis().then(() => {
      if (!alive || !ref.current) return;
      const id = window.google.accounts.id;
      id.initialize({ client_id: googleClientId, callback: (r) => cb.current(r.credential), ux_mode: 'popup', auto_select: false, cancel_on_tap_outside: true });
      ref.current.innerHTML = '';
      id.renderButton(ref.current, {
        type: 'standard', theme: 'outline', size: 'large', shape: 'pill', logo_alignment: 'center',
        text: signup ? 'signup_with' : 'continue_with', locale: lang === 'ar' ? 'ar' : 'fr',
        width: Math.min(400, Math.max(220, ref.current.offsetWidth || 320))
      });
    }).catch(() => { if (alive) setFailed(true); });
    return () => { alive = false; };
  }, [googleClientId, lang, signup]);

  if (!googleClientId || inAppWebView() || failed) return null;
  return <div className="gbtn" ref={ref} />;
}
