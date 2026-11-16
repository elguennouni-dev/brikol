import { useState } from 'react';
import { api, useAsync, useSession, setAuth, setLang, guarded, toast, errMsg, isArtisan, isAdmin, artisanReady, t } from '../session';
import { Avatar, Screen, PageHead, Icon } from '../ui';

function AvailToggle() {
  const me = useAsync(() => api('/artisan/me'), []);
  const [override, setOverride] = useState(null);
  const checked = override != null ? override : !!(me.data && me.data.summary.availableToday);
  const change = (e) => {
    const v = e.target.checked;
    setOverride(v);
    api('/artisan/me', { method: 'PATCH', body: { availableToday: v } }).catch((err) => { setOverride(!v); toast(errMsg(err)); });
  };
  return (
    <label className="card row between g12" style={{ padding: '14px 16px' }}>
      <span className="col" style={{ gap: 2 }}><b>{t('avail')}</b><span className="muted xs">{t('availHint')}</span></span>
      <input type="checkbox" checked={checked} disabled={!me.data} onChange={change} style={{ width: 22, height: 22, accentColor: '#0E2B22' }} aria-label={t('avail')} />
    </label>
  );
}

const ArtisanCta = ({ onClick }) => (
  <div className="card col g8" style={{ background: 'var(--lime)', border: 'none', borderRadius: 24 }}>
    <b style={{ fontSize: 18, maxWidth: 240, lineHeight: 1.3 }}>{t('ctaTitle')}</b>
    <span className="small" style={{ color: 'var(--ink-2)', maxWidth: 250 }}>{t('ctaText')}</span>
    <a className="btn dark sm" style={{ alignSelf: 'flex-start', marginTop: 6, height: 44 }} href="#/login?mode=register&role=ARTISAN" onClick={onClick}>{t('ctaBtn')}</a>
  </div>
);

export default function Me() {
  const { token, user, lang } = useSession();
  const langSeg = (
    <div className="col g10"><span className="h3">{t('langT')}</span>
      <div className="seg">
        <button className={lang === 'ar' ? 'on' : ''} onClick={() => setLang('ar')}>الدارجة</button>
        <button className={lang === 'fr' ? 'on' : ''} onClick={() => setLang('fr')}>Français</button>
      </div>
    </div>
  );

  if (!token || !user) {
    return (
      <Screen tab="me" width="narrow">
        <PageHead title={t('meTitle')} />
        <div className="card col g12"><b style={{ fontSize: 17 }}>{t('notLogged')}</b><span className="muted small">{t('notLoggedText')}</span>
          <div className="row g8"><a className="btn" style={{ flex: 1 }} href="#/login">{t('login')}</a><a className="btn ghost" style={{ flex: 1 }} href="#/login?mode=register">{t('register')}</a></div></div>
        {langSeg}
        <ArtisanCta />
      </Screen>
    );
  }
  const logout = () => { setAuth(null, null); toast(t('logout')); location.hash = '#/'; };
  return (
    <Screen tab="me" width="narrow">
      <PageHead title={t('meTitle')} />
      <div className="card row g12">
        <Avatar name={user.fullName} id={user.id} />
        <div className="col" style={{ gap: 2, minWidth: 0 }}>
          <b style={{ fontSize: 18 }}>{user.fullName}</b>
          <span className="muted small" dir="ltr" style={{ textAlign: 'start' }}>{user.phone}</span>
          <span className="pill pill-lime" style={{ marginTop: 4 }}>{isAdmin() ? t('a_role_ADMIN') : t(isArtisan() ? 'roleArtisan' : 'roleClient')}</span>
        </div>
      </div>
      {isAdmin() ? <a className="btn dark block" href="#/admin"><Icon name="shield" size={18} />{t('a_title')}</a> : null}
      {isArtisan() ? (
        <a className={`btn block ${artisanReady() ? 'ghost' : ''}`} href="#/onboarding"><Icon name="doc" size={18} />{t('openFile')}{artisanReady() ? '' : ' · ' + t('ob_' + (user.artisanStatus || 'DRAFT')).split('.')[0]}</a>
      ) : null}
      {artisanReady() ? <AvailToggle /> : null}
      {langSeg}
      <button className="btn ghost block" onClick={() => guarded(logout)}>{t('logout')}</button>
      {isArtisan() || isAdmin() ? null : <ArtisanCta onClick={() => setAuth(null, null)} />}
    </Screen>
  );
}
