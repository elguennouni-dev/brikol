import { useEffect, useRef, useState } from 'react';
import { IC } from './icons';
import {
  t, L, fmt, initials, ago, useSession, isArtisan, useToast, errMsg, setLang, useHash, parseRoute,
  useNotifs, notifText, notifHref, markRead, markAllRead, isAdmin, useConfig
} from './session';

export { IC };

export function Icon({ name, size = 20, className = '' }) {
  return <svg className={`ic ${className}`} width={size} height={size} viewBox="0 0 24 24" aria-hidden="true"><path d={IC[name] || ''} /></svg>;
}

export function Verified({ size = 17 }) {
  return (
    <svg className="ic vbadge" width={size} height={size} viewBox="0 0 24 24" role="img" aria-label={t('verified')}>
      <path d={IC.disc} style={{ fill: 'currentColor', stroke: 'none' }} />
      <path d={IC.checkMark} style={{ stroke: '#fff', strokeWidth: 2 }} />
    </svg>
  );
}

export const PAL = [['#D6F0DF', '#0E5A36'], ['#FBE6C8', '#8A4B08'], ['#D9E4FA', '#1F3F8F'], ['#F6D9D5', '#8E2A22'], ['#FFF2B8', '#7A5A00'], ['#CFEFEF', '#0B5A5E'], ['#E6DAF7', '#4A2A8A']];
const CAT_PAL = { paint: 0, wood: 1, mech: 2, metal: 3, elec: 4, plumb: 5, tile: 6 };
export const catColors = (code) => (CAT_PAL[code] != null ? PAL[CAT_PAL[code]] : ['#E6EAE2', '#0E2B22']);

export function Avatar({ name, id, className = '' }) {
  const c = PAL[Math.abs(Number(id) || 0) % PAL.length];
  return <div className={`avatar ${className}`} style={{ background: c[0], color: c[1] }}>{initials(name)}</div>;
}

export function Rating({ a }) {
  if (a.reviewCount > 0) {
    return (
      <>
        <Icon name="star" size={14} className="star" />
        <span style={{ fontWeight: 800 }}>{Number(a.rating).toFixed(1)}</span>
        <span className="muted">({a.reviewCount})</span>
      </>
    );
  }
  return <span className="pill pill-lime">{t('newBadge')}</span>;
}

export function ArtisanCard({ a }) {
  return (
    <a className="acard" href={`#/artisan/${a.id}`}>
      <Avatar name={L(a, 'name')} id={a.id} />
      <div className="body">
        <div className="row g6" style={{ minWidth: 0 }}><span className="name">{L(a, 'name')}</span>{a.verified ? <Verified /> : null}</div>
        <div className="row g4 muted small" style={{ minWidth: 0 }}>
          <span style={{ whiteSpace: 'nowrap' }}>{L(a, 'trade')}</span><span>·</span><Icon name="pin" size={13} /><span className="ell">{L(a, 'neighborhood')}</span>
        </div>
        <div className="row g6 small">
          <Rating a={a} />
          <span className="price num">{a.fromPrice != null ? `${t('from')} ${fmt(a.fromPrice)} ${t('dh')}${L(a, 'fromUnit')}` : ''}</span>
        </div>
        <span className={`pill ${a.availableToday ? 'pill-ok' : 'pill-mute'}`}>{t(a.availableToday ? 'availToday' : 'availTomorrow')}</span>
      </div>
    </a>
  );
}

export function CashBanner({ text }) {
  return (
    <div className="banner">
      <div className="dot"><Icon name="cash" size={21} /></div>
      <div className="col" style={{ gap: 2 }}><b style={{ fontSize: 15, color: 'var(--ink)' }}>{t('cashTitle')}</b><span>{text || t('cashText')}</span></div>
    </div>
  );
}

export function SegLinks({ items }) {
  return <div className="seg">{items.map((i) => <a key={i.href} className={i.on ? 'on' : ''} href={i.href}>{i.label}</a>)}</div>;
}

// ------------------------------------------------------------ loading animation
// A khatem (eight-pointed star) that draws itself, spins slowly and pulses.
export function Loader({ label = true, size = 64 }) {
  return (
    <div className="loading" role="status" aria-live="polite">
      <div className="khatem" style={{ width: size, height: size }}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle className="k-ring" cx="12" cy="12" r="10.6" pathLength="100" />
          <path className="k-back" d={IC.tile} pathLength="100" />
          <path className="k-draw" d={IC.tile} pathLength="100" />
        </svg>
        <span className="k-dot" />
      </div>
      {label ? <div className="k-label">{t('loading')}<i>.</i><i>.</i><i>.</i></div> : null}
    </div>
  );
}

// Skeleton placeholders keep the layout stable while data arrives.
export function Skeleton({ rows = 3 }) {
  return (
    <div className="col g12" aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => <div key={i} className="skel" style={{ animationDelay: `${i * 120}ms` }} />)}
    </div>
  );
}

export function Empty({ children, onRetry }) {
  return (
    <div className="empty">
      {children}
      {onRetry ? <><br /><br /><button className="btn sm" onClick={onRetry}>{t('retry')}</button></> : null}
    </div>
  );
}

// Shows the loader on first load and the error box with a retry button on failure.
export function Gate({ st, children, loader }) {
  if (st.error) return <Empty onRetry={st.reload}>{errMsg(st.error)}</Empty>;
  if (!st.data) return loader || <Loader />;
  return children(st.data);
}

// ------------------------------------------------------------ app chrome
export function Nav({ active }) {
  useSession();
  const art = isArtisan();
  const adm = isAdmin();
  const Item = ({ id, href, icon, label }) => (
    <a href={href} className={active === id ? 'on' : ''} aria-current={active === id ? 'page' : undefined}>
      <span className="p"><Icon name={icon} size={22} /></span><span>{label}</span>
    </a>
  );
  if (adm) {
    return (
      <nav className="nav admin-nav">
        <Item id="home" href="#/" icon="home" label={t('nav_home')} />
        <Item id="admin" href="#/admin" icon="shield" label={t('a_open')} />
        <Item id="me" href="#/me" icon="user" label={t('nav_me')} />
      </nav>
    );
  }
  return (
    <nav className="nav">
      <Item id="home" href="#/" icon="home" label={t('nav_home')} />
      <Item id="explore" href="#/explore" icon="compass" label={t('nav_explore')} />
      <div className="plus">
        {art
          ? <a href="#/jobs" aria-label={t('nav_jobs')}><Icon name="clip" size={26} /></a>
          : <a href="#/new" aria-label={t('nav_new')}><Icon name="plus" size={26} /></a>}
      </div>
      <Item id="req" href={art ? '#/jobs?tab=mine' : '#/requests'} icon="clip" label={art ? t('nav_offers') : t('nav_req')} />
      <Item id="me" href="#/me" icon="user" label={t('nav_me')} />
    </nav>
  );
}

// ------------------------------------------------------------ notifications UI
const NOTIF_ICON = { NEW_REQUEST: 'clip', OFFER_RECEIVED: 'cash', OFFER_ACCEPTED: 'phone', OFFER_REJECTED: 'user', REQUEST_CANCELLED: 'minus', JOB_DONE: 'checkMark', REVIEW_RECEIVED: 'star', ANNOUNCEMENT: 'bell', ADMIN_CANCELLED: 'minus', OFFER_COUNTERED: 'cash', OFFER_AGREED: 'phone', ARTISAN_APPROVED: 'checkMark', ARTISAN_REJECTED: 'doc', ARTISAN_SUBMITTED: 'doc' };
const NOTIF_TONE = { NEW_REQUEST: 'lime', OFFER_RECEIVED: 'lime', OFFER_ACCEPTED: 'ok', OFFER_REJECTED: 'mute', REQUEST_CANCELLED: 'warn', JOB_DONE: 'ok', REVIEW_RECEIVED: 'warn', ANNOUNCEMENT: 'lime', ADMIN_CANCELLED: 'warn', OFFER_COUNTERED: 'lime', OFFER_AGREED: 'ok', ARTISAN_APPROVED: 'ok', ARTISAN_REJECTED: 'warn', ARTISAN_SUBMITTED: 'lime' };

export function NotifItem({ n, onClick }) {
  return (
    <a className={`nitem ${n.read ? '' : 'unread'}`} href={notifHref(n)} onClick={() => { markRead(n); if (onClick) onClick(); }}>
      <span className={`nic tone-${NOTIF_TONE[n.kind] || 'mute'}`}><Icon name={NOTIF_ICON[n.kind] || 'bell'} size={19} /></span>
      <span className="grow col" style={{ gap: 2 }}>
        <span className="ntxt">{notifText(n)}</span>
        <span className="muted xs">{ago(n.createdAt)}</span>
      </span>
      {n.read ? null : <span className="ndot" aria-label="new" />}
    </a>
  );
}

// popover = on desktop the bell opens a small panel; on a phone it opens the notifications page.
export function Bell({ popover = false }) {
  const { token } = useSession();
  const { unread, items, live } = useNotifs();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const away = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', away);
    return () => document.removeEventListener('mousedown', away);
  }, [open]);
  useEffect(() => {
    const close = () => setOpen(false);
    window.addEventListener('hashchange', close);
    return () => window.removeEventListener('hashchange', close);
  }, []);
  if (!token) return null;
  return (
    <div className="bellwrap" ref={ref}>
      <a className="iconbtn bell" href="#/notifications" aria-label={t('notifTitle')}
        onClick={(e) => { if (popover) { e.preventDefault(); setOpen((o) => !o); } }}>
        <Icon name="bell" size={20} />
        {unread > 0 ? <span key={unread} className="badge">{unread > 9 ? '9+' : unread}</span> : null}
        {live ? <span className="livedot" title={t('notifLive')} /> : null}
      </a>
      {popover && open ? (
        <div className="pop" role="dialog" aria-label={t('notifTitle')}>
          <div className="row between" style={{ padding: '14px 16px 8px' }}>
            <b>{t('notifTitle')}</b>
            {unread > 0 ? <button className="link" onClick={markAllRead}>{t('notifReadAll')}</button> : null}
          </div>
          {items.length ? items.slice(0, 8).map((n) => <NotifItem key={n.id} n={n} onClick={() => setOpen(false)} />)
            : <div className="empty" style={{ padding: '24px 16px' }}>{t('notifEmpty')}</div>}
          <a className="pop-foot" href="#/notifications">{t('seeAll')}</a>
        </div>
      ) : null}
    </div>
  );
}

// Title row with the bell on phones (on desktop the bell lives in the top bar).
export function PageHead({ title, children }) {
  return (
    <div className="row between g12">
      <h1 className="h1">{title}</h1>
      <div className="row g8">{children}<span className="m-only"><Bell /></span></div>
    </div>
  );
}

// ------------------------------------------------------------ desktop top bar (hidden on phones)
export function TopBar() {
  const { token, user, lang } = useSession();
  const name = parseRoute(useHash()).name;
  const art = isArtisan();
  const link = (href, label, on) => <a href={href} className={on ? 'on' : ''}>{label}</a>;
  return (
    <header className="topbar">
      <div className="topbar-in">
        <a className="tb-brand" href="#/" aria-label="Brikol"><img src="/app/brand/logo.png" alt="Brikol" /></a>
        <nav className="tb-links">
          {link('#/', t('nav_home'), name === 'home')}
          {link('#/explore', t('nav_explore'), name === 'explore' || name === 'artisan')}
          {isAdmin() ? link('#/admin', t('a_open'), name === 'admin') : null}
          {token && !isAdmin() ? (art ? link('#/jobs', t('nav_jobs'), name === 'jobs') : link('#/requests', t('nav_req'), name === 'requests')) : null}
        </nav>
        <div className="grow" />
        {art || isAdmin() ? null : <a className="btn sm" href="#/new"><Icon name="plus" size={16} />{t('heroBtn')}</a>}
        <Bell popover />
        <LangButton onClick={() => setLang(lang === 'ar' ? 'fr' : 'ar')} />
        {token && user ? (
          <a className="tb-user" href="#/me"><Avatar name={user.fullName} id={user.id} /><span>{String(user.fullName || '').split(' ')[0]}</span></a>
        ) : (
          <>
            <a className="btn sm ghost" href="#/login">{t('login')}</a>
            <a className="btn sm dark" href="#/login?mode=register">{t('register')}</a>
          </>
        )}
      </div>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer className="site-foot">
      <span>© Brikol · {t('footerText')}</span>
      <a href="/">{t('siteHome')}</a>
    </footer>
  );
}

// width: 'wide' (home, explore, artisan, new request), 'narrow' (lists, profile), 'xs' (login).
// On a phone the shell is invisible (display: contents) and the layout is the same as before.
export function SiteBanner() {
  const { bannerMessage } = useConfig();
  return bannerMessage ? <div className="site-banner" role="status"><Icon name="bell" size={16} />{bannerMessage}</div> : null;
}

export function Screen({ children, bar, tab, nav = true, flush = false, width = 'wide' }) {
  return (
    <>
      <TopBar />
      <SiteBanner />
      <div className={`shell ${width} ${bar ? 'with-bar' : ''}`}>
        <div className={`scroll view-in ${flush ? 'flush' : ''}`}>{children}</div>
        {bar}
      </div>
      <SiteFooter />
      {nav ? <Nav active={tab} /> : null}
    </>
  );
}

export function Toast() {
  const { msg, show } = useToast();
  return <div id="toast" className={show ? 'show' : ''} role="status" aria-live="assertive">{msg}</div>;
}

export function BackButton({ onClick }) {
  return <button className="iconbtn" onClick={onClick} aria-label={t('back')}><Icon name="back" size={20} className="flip" /></button>;
}

export function LangButton({ onClick }) {
  return <button className="langbtn" onClick={onClick} aria-label={t('switchLang')}><Icon name="globe" size={17} />{t('otherLang')}</button>;
}
