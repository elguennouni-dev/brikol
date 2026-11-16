import { useEffect, useState } from 'react';
import { api, getCats, useAsync, setAuth, takeNext, setLang, useSession, guarded, toast, t, L, back } from '../session';
import { Icon, Screen, BackButton, LangButton } from '../ui';
import { GoogleButton, tokenProfile, inAppWebView } from '../google';
import { useConfig, errMsg } from '../session';

export default function Login({ route }) {
  const { lang } = useSession();
  const qMode = route.q.get('mode') === 'register' ? 'register' : 'login';
  const qRole = route.q.get('role') === 'ARTISAN' ? 'ARTISAN' : 'CLIENT';
  const [mode, setMode] = useState(qMode);
  const [role, setRole] = useState(qRole);
  const [f, setF] = useState({ phone: '', password: '', fullName: '', cat: '', hood: '' });
  const [busy, setBusy] = useState(false);
  const [gpend, setGpend] = useState(null); // Google account that still needs phone + role
  const { googleClientId } = useConfig();
  const showGoogle = !!googleClientId && !inAppWebView();
  useEffect(() => { setMode(qMode); setRole(qRole); }, [qMode, qRole]);
  const cats = useAsync(getCats, []);
  const reg = mode === 'register';
  const bind = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) });

  const done = (res) => {
    setAuth(res.token, res.user);
    toast(t('welcome'));
    const home = res.user.role === 'ADMIN' ? '#/admin' : (res.user.role === 'ARTISAN' && res.user.artisanStatus !== 'APPROVED' ? '#/onboarding' : '#/');
    location.hash = takeNext() || home;
  };

  const onGoogle = (credential) => {
    setBusy(true);
    api('/auth/google', { method: 'POST', body: { credential } })
      .then(done)
      .catch((e) => {
        if (e && e.code === 'GOOGLE_SIGNUP_NEEDED') { setGpend({ credential, ...tokenProfile(credential) }); setMode('register'); }
        else if (!(e && e.handled)) toast(errMsg(e));
      })
      .finally(() => setBusy(false));
  };

  const finishGoogle = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      const body = { credential: gpend.credential, phone: f.phone, role };
      if (role === 'ARTISAN') { body.categoryCode = f.cat; body.neighborhood = f.hood || null; }
      try {
        done(await api('/auth/google', { method: 'POST', body }));
      } catch (err) {
        if (err && err.code === 'GOOGLE_INVALID') setGpend(null); // the Google token expired: pick the account again
        throw err;
      }
    }).then(() => setBusy(false));
  };

  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      let res;
      if (reg) {
        const body = { phone: f.phone, password: f.password, fullName: f.fullName, city: 'Casablanca', role };
        if (role === 'ARTISAN') { body.categoryCode = f.cat; body.neighborhood = f.hood || null; }
        res = await api('/auth/register', { method: 'POST', body });
      } else {
        res = await api('/auth/login', { method: 'POST', body: { phone: f.phone, password: f.password } });
      }
      done(res);
    }).then(() => setBusy(false));
  };

  const roleSeg = (
    <div><span className="lbl">{t('iAm')}</span>
      <div className="seg">
        <button type="button" className={role === 'CLIENT' ? 'on' : ''} onClick={() => setRole('CLIENT')}>{t('roleClient')}</button>
        <button type="button" className={role === 'ARTISAN' ? 'on' : ''} onClick={() => setRole('ARTISAN')}>{t('roleArtisan')}</button>
      </div>
    </div>
  );
  const tradeFields = role === 'ARTISAN' ? (
    <>
      <div><label className="lbl" htmlFor="tr">{t('trade')}</label>
        <select id="tr" className="select" {...bind('cat')}><option value="">{t('chooseTrade')}</option>{(cats.data || []).map((c) => <option key={c.code} value={c.code}>{L(c, 'name')}</option>)}</select></div>
      <div><label className="lbl" htmlFor="hd">{t('neighborhood')}</label><input id="hd" className="input" maxLength={80} {...bind('hood')} /></div>
    </>
  ) : null;

  if (gpend) {
    return (
      <Screen tab="me" nav={false} width="xs">
        <div className="row between">
          <BackButton onClick={() => setGpend(null)} />
          <LangButton onClick={() => setLang(lang === 'ar' ? 'fr' : 'ar')} />
        </div>
        <div className="col g8">
          <h1 className="h1">{t('gFinishTitle')}</h1>
          <span className="muted">{t('gFinishSub')}</span>
        </div>
        <div className="card row g12">
          {gpend.picture ? <img className="avatar" src={gpend.picture} alt="" referrerPolicy="no-referrer" /> : <div className="avatar">G</div>}
          <div className="col" style={{ gap: 2, minWidth: 0 }}><b className="ell">{gpend.name}</b><span className="muted small ell" dir="ltr" style={{ textAlign: 'start' }}>{gpend.email}</span></div>
        </div>
        <form onSubmit={finishGoogle} className="col g16" noValidate>
          <div><label className="lbl" htmlFor="gph">{t('phone')}</label><input id="gph" className="input" type="tel" inputMode="tel" dir="ltr" style={{ textAlign: 'start' }} placeholder={t('phonePh')} autoComplete="tel" {...bind('phone')} />
            <span className="muted xs" style={{ display: 'block', marginTop: 6 }}>{t('gPhoneWhy')}</span></div>
          {roleSeg}
          {tradeFields}
          <button className="btn block" type="submit" disabled={busy}>{t('createAccount')}</button>
        </form>
      </Screen>
    );
  }

  return (
    <Screen tab="me" nav={false} width="xs">
      <div className="row between">
        <BackButton onClick={back} />
        <LangButton onClick={() => setLang(lang === 'ar' ? 'fr' : 'ar')} />
      </div>
      <div className="col g8">
        <img className="brand-mark" src="/app/brand/mark.png" alt="Brikol" />
        <h1 className="h1" style={{ marginTop: 8 }}>{t('hello2')}</h1>
        <span className="muted">{reg ? t('registerSub') : t('loginSub')}</span>
      </div>
      <div className="seg">
        <button className={reg ? '' : 'on'} onClick={() => setMode('login')}>{t('login')}</button>
        <button className={reg ? 'on' : ''} onClick={() => setMode('register')}>{t('register')}</button>
      </div>
      {showGoogle ? (
        <>
          <GoogleButton onCredential={onGoogle} signup={reg} />
          <div className="ordiv"><span>{t('orPhone')}</span></div>
        </>
      ) : null}
      <form onSubmit={submit} className="col g16" noValidate>
        {reg ? <div><label className="lbl" htmlFor="fn">{t('fullName')}</label><input id="fn" className="input" autoComplete="name" maxLength={120} {...bind('fullName')} /></div> : null}
        <div><label className="lbl" htmlFor="ph">{t('phone')}</label><input id="ph" className="input" type="tel" inputMode="tel" dir="ltr" style={{ textAlign: 'start' }} placeholder={t('phonePh')} autoComplete="tel" {...bind('phone')} /></div>
        <div><label className="lbl" htmlFor="pw">{t('password')}</label><input id="pw" className="input" type="password" autoComplete={reg ? 'new-password' : 'current-password'} {...bind('password')} /></div>
        {reg ? roleSeg : null}
        {reg ? tradeFields : null}
        <button className="btn block" type="submit" disabled={busy}>{reg ? t('createAccount') : t('login')}</button>
      </form>
    </Screen>
  );
}
