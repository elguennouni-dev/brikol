import { useEffect } from 'react';
import { api, useHash, parseRoute, useSession, setAuth, setNext, isArtisan, isAdmin, artisanReady, refreshMe, startLive, stopLive, loadConfig } from './session';
import { Toast, Loader } from './ui';
import Home from './views/Home';
import Explore from './views/Explore';
import Artisan from './views/Artisan';
import NewRequest from './views/NewRequest';
import Requests from './views/Requests';
import Jobs from './views/Jobs';
import Me from './views/Me';
import Login from './views/Login';
import Notifications from './views/Notifications';
import Admin from './views/Admin';
import Onboarding from './views/Onboarding';

const VIEWS = { home: Home, explore: Explore, artisan: Artisan, new: NewRequest, requests: Requests, jobs: Jobs, me: Me, login: Login, notifications: Notifications, admin: Admin, onboarding: Onboarding };

function guard(name, token, user) {
  if ((name === 'new' || name === 'requests' || name === 'jobs' || name === 'notifications') && !token) { setNext(location.hash || '#/'); return '#/login'; }
  if (name === 'onboarding' && !token) { setNext('#/onboarding'); return '#/login'; }
  if (name === 'onboarding' && user && !isArtisan()) return '#/';
  if (name === 'jobs' && user && isArtisan() && !artisanReady()) return '#/onboarding';
  if (name === 'admin' && !token) { setNext(location.hash || '#/admin'); return '#/login'; }
  if (name === 'admin' && user && !isAdmin()) return '#/';
  if ((name === 'new' || name === 'requests' || name === 'jobs') && isAdmin()) return '#/admin';
  if ((name === 'new' || name === 'requests') && isArtisan()) return '#/jobs';
  if (name === 'jobs' && user && !isArtisan()) return '#/requests';
  return null;
}

export default function App() {
  const hash = useHash();
  const { token, user } = useSession();
  const r = parseRoute(hash);
  const name = VIEWS[r.name] ? r.name : 'home';
  const redirect = guard(name, token, user);

  useEffect(() => { if (redirect) location.replace(redirect); }, [redirect]);
  useEffect(() => { loadConfig(); refreshMe(); }, []);
  useEffect(() => { window.scrollTo(0, 0); }, [name, r.id]);
  // Live notifications: one open connection while logged in.
  useEffect(() => {
    if (token) startLive(); else stopLive();
    return () => stopLive();
  }, [token]);
  // A stored token without a stored user (should be rare): fetch the profile once.
  useEffect(() => {
    if (token && !user) api('/me').then((u) => setAuth(token, u)).catch(() => { /* handled in api() */ });
  }, [token, user]);

  const View = VIEWS[name];
  return (
    <>
      {redirect ? <Loader /> : <View key={name} route={r} />}
      <Toast />
    </>
  );
}
