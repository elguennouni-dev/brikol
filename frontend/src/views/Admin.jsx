import { useState } from 'react';
import { api, useAsync, useSession, isAdmin, isOwner, can, guarded, toast, t, L, fmt, ago, getSession, money } from '../session';
import { RequestPlace, MapPreview } from '../places';
import { MediaGallery, PrivateImg, Lightbox } from '../media';
import { Screen, Icon, Gate, Skeleton, Empty, BackButton } from '../ui';

// [route, label, who may open it]
const SECTIONS = [['', 'a_dash', () => true], ['files', 'a_files', () => can('FILES')], ['cases', 'a_cases', () => can('REPORTS')], ['reports', 'a_reports', () => can('REPORTS')],
  ['users', 'a_users', () => can('USERS')], ['requests', 'a_requests', () => can('REQUESTS')], ['reviews', 'a_reviews', () => can('REQUESTS')],
  ['categories', 'a_cats', () => can('CATEGORIES')], ['audit', 'a_audit', () => can('AUDIT')],
  ['tools', 'a_tools', () => can('BILLING') || can('BROADCAST') || can('SETTINGS') || can('USERS') || can('REQUESTS')], ['team', 'a_team', () => isOwner()]];
const ALLOWED = { file: 'files', user: 'users', request: 'requests', case: 'cases' };
const STATUS_PILL = { OPEN: 'pill-lime', ACCEPTED: 'pill-warn', DONE: 'pill-ok', CANCELLED: 'pill-mute' };
const statusLabel = (s) => t('a_status' + s.charAt(0) + s.slice(1).toLowerCase());
const qs = (o) => Object.entries(o).filter(([, v]) => v !== '' && v != null).map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');
const when = (iso) => (iso ? new Date(iso).toLocaleString(getSession().lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '');
const catLabel = (r) => (getSession().lang === 'ar' ? r.categoryNameAr : r.categoryNameFr);

// Two-step button: first tap asks, second tap does it (no confirm() dialogs in WebViews).
function Confirm({ label, onYes, danger, children }) {
  const [ask, setAsk] = useState(false);
  if (!ask) return <button className={`btn sm ${danger ? 'dark' : 'ghost'}`} onClick={() => setAsk(true)}>{label}</button>;
  return (
    <span className="col g8" style={{ alignItems: 'flex-start' }}>
      {children}
      <span className="row g8">
        <button className="btn sm dark" onClick={() => { setAsk(false); onYes(); }}>{t('a_confirm')}</button>
        <button className="btn sm ghost" onClick={() => setAsk(false)}>{t('a_cancel')}</button>
      </span>
    </span>
  );
}

function Pager({ page, size, total, onPage }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="row between g12 pager">
      <span className="muted small">{t('a_total', { n: fmt(total) })}</span>
      <span className="row g8">
        <button className="btn sm ghost" disabled={page <= 0} onClick={() => onPage(page - 1)}>{t('a_prev')}</button>
        <span className="muted small">{t('a_page', { p: page + 1, n: pages })}</span>
        <button className="btn sm ghost" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>{t('a_next')}</button>
      </span>
    </div>
  );
}

function Bars({ data, label }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  return (
    <div className="card col g8">
      <div className="row between"><b>{label}</b><span className="muted xs">{t('a_perDay')}</span></div>
      <div className="a-bars" role="img" aria-label={label}>
        {data.map((d) => (
          <div key={d.day} className="a-bar" title={`${d.day}: ${d.count}`}>
            <span className="a-bar-n">{d.count || ''}</span>
            <i style={{ height: `${Math.max(3, (d.count / max) * 100)}%` }} />
          </div>
        ))}
      </div>
    </div>
  );
}

const Stat = ({ n, label, tone }) => <div className={`a-stat ${tone || ''}`}><b className="num">{n}</b><span>{label}</span></div>;

// ------------------------------------------------------------ sections
function Dashboard() {
  const st = useAsync(() => api('/admin/stats'), []);
  return (
    <Gate st={st} loader={<Skeleton rows={5} />}>{(s) => (
      <>
        {s.openReports && can('REPORTS') ? (
          <a className="note wait" href="#/admin/reports" style={{ textDecoration: 'none' }}><Icon name="flag" size={18} /><span className="grow">{t('a_openReports')}</span><b className="num">{s.openReports} ›</b></a>
        ) : null}
        {s.pendingArtisans && can('FILES') ? (
          <a className="note wait" href="#/admin/files" style={{ textDecoration: 'none' }}><Icon name="doc" size={18} /><span className="grow">{t('a_pendingFiles')}</span><b className="num">{s.pendingArtisans} ›</b></a>
        ) : null}
        <div className="a-stats">
          <Stat n={fmt(s.users)} label={t('a_totalUsers')} />
          <Stat n={fmt(s.clients)} label={t('a_clients')} />
          <Stat n={fmt(s.artisans)} label={t('a_artisans')} />
          <Stat n={fmt(s.newUsers7)} label={t('a_new7')} tone="lime" />
          <Stat n={fmt(s.blocked)} label={t('a_blocked')} tone={s.blocked ? 'warn' : ''} />
          <Stat n={fmt(s.verifiedArtisans)} label={t('a_verified')} />
          <Stat n={fmt(s.requests)} label={t('a_totalReq')} />
          <Stat n={fmt(s.open)} label={t('a_statusOpen')} tone="lime" />
          <Stat n={fmt(s.accepted)} label={t('a_statusAccepted')} />
          <Stat n={fmt(s.done)} label={t('a_statusDone')} />
          <Stat n={fmt(s.cancelled)} label={t('a_statusCancelled')} />
          <Stat n={fmt(s.offers)} label={t('a_offers')} />
          <Stat n={`${s.avgRating || '–'}★`} label={`${t('a_avg')} (${fmt(s.reviews)})`} />
          <Stat n={fmt(s.liveConnections)} label={t('a_live')} tone="ok" />
        </div>
        <div className="grid2">
          <Bars data={s.requestsPerDay} label={t('a_reqPerDay')} />
          <Bars data={s.usersPerDay} label={t('a_usrPerDay')} />
        </div>
        <div className="card col g8">
          <b>{t('a_byCat')}</b>
          {s.categories.map((c) => (
            <div key={c.code} className="row between g12 line"><span>{L(c, 'name')}</span><span className="muted small">{t('a_catUsed', { a: c.artisans, r: c.requests })}</span></div>
          ))}
        </div>
      </>
    )}</Gate>
  );
}

function Users() {
  const [f, setF] = useState({ q: '', role: '', status: '', page: 0 });
  const [draft, setDraft] = useState('');
  const st = useAsync(() => api('/admin/users?' + qs({ ...f, size: 15 })), [f.q, f.role, f.status, f.page]);
  const set = (patch) => setF((x) => ({ ...x, page: 0, ...patch }));
  return (
    <>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); set({ q: draft }); }}>
        <input className="inp" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={t('a_search')} aria-label={t('a_search')} />
        <select className="inp" value={f.role} onChange={(e) => set({ role: e.target.value })}>
          <option value="">{t('a_allRoles')}</option>
          {['CLIENT', 'ARTISAN', 'ADMIN'].map((r) => <option key={r} value={r}>{t('a_role_' + r)}</option>)}
        </select>
        <select className="inp" value={f.status} onChange={(e) => set({ status: e.target.value })}>
          <option value="">{t('a_allStatus')}</option>
          <option value="active">{t('a_active')}</option>
          <option value="blocked">{t('a_blockedS')}</option>
        </select>
      </form>
      <Gate st={st} loader={<Skeleton rows={6} />}>{(p) => (p.items.length ? (
        <>
          <div className="card tbl">
            {p.items.map((u) => (
              <a key={u.id} className="trow" href={`#/admin/user/${u.id}`}>
                <span className="grow col" style={{ gap: 2, minWidth: 0 }}>
                  <b className="ell">{u.fullName}</b>
                  <span className="muted xs" dir="ltr" style={{ textAlign: 'start' }}>{u.phone} · {u.city}</span>
                </span>
                <span className="row g6 wrap">
                  {u.blocked ? <span className="pill pill-warn">{t('a_blockedS')}</span> : null}
                  {u.verified ? <span className="pill pill-ok">{t('verified')}</span> : null}
                  <span className="pill pill-mute">{t('a_role_' + u.role)}</span>
                </span>
              </a>
            ))}
          </div>
          <Pager page={p.page} size={p.size} total={p.total} onPage={(page) => setF((x) => ({ ...x, page }))} />
        </>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

function Field({ k, v }) { return <div className="kv"><span className="muted small">{k}</span><b dir="auto">{v}</b></div>; }

function UserDetail({ id }) {
  const st = useAsync(() => api('/admin/users/' + id), [id]);
  const [reason, setReason] = useState('');
  const [temp, setTemp] = useState(null);
  const act = (fn) => guarded(async () => { await fn(); toast(t('a_done')); st.reload(); });
  return (
    <Gate st={st} loader={<Skeleton rows={5} />}>{(d) => {
      const u = d.user;
      const me = getSession().user;
      const self = me && me.id === u.id;
      const locked = self || u.role === 'ADMIN';
      return (
        <>
          <div className="card col g12">
            <div className="row between g12 wrap">
              <b style={{ fontSize: 20 }}>{u.fullName}</b>
              <span className="row g6">
                {u.blocked ? <span className="pill pill-warn">{t('a_blockedS')}</span> : <span className="pill pill-ok">{t('a_active')}</span>}
                <span className="pill pill-mute">{t('a_role_' + u.role)}</span>
              </span>
            </div>
            {u.blocked && u.blockedReason ? <div className="note wait">{t('a_blockedWhy', { r: u.blockedReason })}</div> : null}
            <div className="kvs">
              <Field k={t('a_phone')} v={<span dir="ltr">{u.phone}</span>} />
              <Field k={t('a_city')} v={u.city} />
              <Field k={t('a_joined')} v={when(u.createdAt)} />
              <Field k={t('a_lastLogin')} v={u.lastLoginAt ? when(u.lastLoginAt) : t('a_never')} />
              <Field k={t('a_requests')} v={fmt(u.requests)} />
              {u.artisanId ? <Field k={t('a_offers')} v={fmt(u.offers)} /> : null}
              {d.tradeFr ? <Field k={t('trade')} v={getSession().lang === 'ar' ? d.tradeAr : d.tradeFr} /> : null}
              {d.reviewCount ? <Field k={t('a_reviews')} v={`${Number(d.rating).toFixed(1)}★ (${d.reviewCount})`} /> : null}
            </div>
          </div>

          {locked ? null : (
            <div className="card col g12">
              {u.blocked
                ? <button className="btn sm" onClick={() => act(() => api(`/admin/users/${u.id}/unblock`, { method: 'POST' }))}>{t('a_unblock')}</button>
                : (
                  <Confirm label={t('a_block')} danger onYes={() => act(() => api(`/admin/users/${u.id}/block`, { method: 'POST', body: { reason } }))}>
                    <input className="inp" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder={t('a_reason')} />
                  </Confirm>
                )}
              {u.artisanId && can('FILES') ? <a className="btn sm ghost" href={`#/admin/file/${u.artisanId}`}><Icon name="doc" size={16} />{t('a_files')}</a> : null}
              {u.artisanId && can('FILES') ? (
                <button className="btn sm ghost" onClick={() => act(() => api(`/admin/artisans/${u.artisanId}/verified`, { method: 'POST', body: { verified: !u.verified } }))}>
                  <Icon name="checkMark" size={16} />{u.verified ? t('a_unverify') : t('a_verify')}
                </button>
              ) : null}
              <Confirm label={t('a_reset')} onYes={() => guarded(async () => { const r = await api(`/admin/users/${u.id}/reset-password`, { method: 'POST' }); setTemp(r.password); })} />
              {temp ? <div className="note ok"><div className="small">{t('a_tempPass')}</div><code className="temp" dir="ltr">{temp}</code></div> : null}
            </div>
          )}

          {u.artisanId && can('BILLING') ? <ArtisanPlan artisanId={u.artisanId} /> : null}
          {d.recentRequests.length ? (
            <div className="card col g8">
              <b>{t('a_recentReq')}</b>
              {d.recentRequests.map((r) => <RequestLine key={r.id} r={r} />)}
            </div>
          ) : null}
          {u.role !== 'ADMIN' && can('REPORTS') ? <UserStrikes userId={u.id} /> : null}
          {d.activity.length ? (
            <div className="card col g8">
              <b>{t('a_activity')}</b>
              {d.activity.map((a) => <AuditLine key={a.id} a={a} />)}
            </div>
          ) : null}
        </>
      );
    }}</Gate>
  );
}

function RequestLine({ r }) {
  return (
    <a className="trow" href={`#/admin/request/${r.id}`}>
      <span className="grow col" style={{ gap: 2, minWidth: 0 }}>
        <b className="ell">#{r.id} · {catLabel(r)}</b>
        <span className="muted xs">{r.clientName} · {r.city} · {ago(r.createdAt)}</span>
      </span>
      <span className="row g6"><span className="muted small">{fmt(r.offers)} {t('a_offers')}</span><span className={`pill ${STATUS_PILL[r.status]}`}>{statusLabel(r.status)}</span></span>
    </a>
  );
}

function Requests() {
  const [f, setF] = useState({ q: '', status: '', category: '', page: 0 });
  const [draft, setDraft] = useState('');
  const cats = useAsync(() => api('/categories'), []);
  const st = useAsync(() => api('/admin/requests?' + qs({ ...f, size: 15 })), [f.q, f.status, f.category, f.page]);
  const set = (patch) => setF((x) => ({ ...x, page: 0, ...patch }));
  return (
    <>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); set({ q: draft }); }}>
        <input className="inp" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={t('a_search')} aria-label={t('a_search')} />
        <select className="inp" value={f.status} onChange={(e) => set({ status: e.target.value })}>
          <option value="">{t('a_allStatus')}</option>
          {['OPEN', 'ACCEPTED', 'DONE', 'CANCELLED'].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
        </select>
        <select className="inp" value={f.category} onChange={(e) => set({ category: e.target.value })}>
          <option value="">{t('a_cats')}</option>
          {(cats.data || []).map((c) => <option key={c.code} value={c.code}>{L(c, 'name')}</option>)}
        </select>
      </form>
      <Gate st={st} loader={<Skeleton rows={6} />}>{(p) => (p.items.length ? (
        <>
          <div className="card tbl">{p.items.map((r) => <RequestLine key={r.id} r={r} />)}</div>
          <Pager page={p.page} size={p.size} total={p.total} onPage={(page) => setF((x) => ({ ...x, page }))} />
        </>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

function RequestDetail({ id }) {
  const st = useAsync(() => api('/admin/requests/' + id), [id]);
  const [reason, setReason] = useState('');
  return (
    <Gate st={st} loader={<Skeleton rows={4} />}>{(d) => {
      const r = d.request;
      return (
        <>
          <div className="card col g12">
            <div className="row between g12 wrap"><b style={{ fontSize: 19 }}>#{r.id} · {catLabel(r)}</b><span className={`pill ${STATUS_PILL[r.status]}`}>{statusLabel(r.status)}</span></div>
            <div className="kvs">
              <Field k={t('a_client')} v={r.clientName} />
              <Field k={t('a_phone')} v={<span dir="ltr">{r.clientPhone}</span>} />
              <Field k={t('a_city')} v={`${r.city}${d.neighborhood ? ' · ' + d.neighborhood : ''}`} />
              <Field k={t('a_budget')} v={r.budget ? `${fmt(r.budget)} ${t('dh')}` : '–'} />
              <Field k={t('a_joined')} v={when(r.createdAt)} />
            </div>
            {d.lat != null ? <RequestPlace r={{ lat: d.lat, lng: d.lng, address: d.address || `${d.lat}, ${d.lng}` }} /> : null}
            <div><span className="muted small">{t('a_description')}</span><p style={{ margin: '4px 0 0' }} dir="auto">{d.description}</p></div>
            <MediaGallery media={d.media} />
            <span className="muted xs">{t('a_priceRange')}: {fmt(d.priceMin)} – {fmt(d.priceMax)} {t('dh')}</span>
            {d.reviewRating ? <div className="note ok">{t('a_reviewBody')}: {d.reviewRating}★ {d.reviewBody || ''}</div> : null}
          </div>
          <div className="card col g8">
            <b>{t('a_offersList')}</b>
            {d.offers.length ? d.offers.map((o) => (
              <div key={o.id} className="row between g12 line"><span className="col" style={{ gap: 2 }}><b>{o.artisanName}</b><span className="muted xs">{o.etaText} {o.message ? '· ' + o.message : ''}</span></span><span className="row g6"><b className="num">{fmt(o.price)} {t('dh')}</b><span className={`pill ${o.status === 'ACCEPTED' ? 'pill-ok' : 'pill-mute'}`}>{o.status}</span></span></div>
            )) : <span className="muted small">{t('a_noOffers')}</span>}
          </div>
          {r.status === 'OPEN' || r.status === 'ACCEPTED' ? (
            <div className="card">
              <Confirm label={t('a_cancelReq')} danger onYes={() => guarded(async () => { await api(`/admin/requests/${r.id}/cancel`, { method: 'POST', body: { reason } }); toast(t('a_done')); st.reload(); })}>
                <input className="inp" value={reason} maxLength={160} onChange={(e) => setReason(e.target.value)} placeholder={t('a_cancelNote')} />
              </Confirm>
            </div>
          ) : null}
        </>
      );
    }}</Gate>
  );
}

function Reviews() {
  const [page, setPage] = useState(0);
  const st = useAsync(() => api('/admin/reviews?' + qs({ page, size: 15 })), [page]);
  return (
    <Gate st={st} loader={<Skeleton rows={5} />}>{(p) => (p.items.length ? (
      <>
        <div className="card tbl">
          {p.items.map((r) => (
            <div key={r.id} className="trow">
              <span className="grow col" style={{ gap: 2, minWidth: 0 }}>
                <b>{r.rating}★ · {r.artisanName}</b>
                <span className="muted xs" dir="auto">{r.clientName} · {ago(r.createdAt)}</span>
                {r.body ? <span className="small" dir="auto">{r.body}</span> : null}
              </span>
              <Confirm label={t('a_delete')} onYes={() => guarded(async () => { await api('/admin/reviews/' + r.id, { method: 'DELETE' }); toast(t('a_done')); st.reload(); })} />
            </div>
          ))}
        </div>
        <Pager page={p.page} size={p.size} total={p.total} onPage={setPage} />
      </>
    ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
  );
}

function CategoryForm({ initial, onSaved, onCancel }) {
  const [c, setC] = useState(initial || { code: '', nameAr: '', nameFr: '', sortOrder: 99, minPrice: 100, maxPrice: 50000 });
  const edit = !!initial;
  const save = (e) => {
    e.preventDefault();
    guarded(async () => {
      const body = { code: c.code, nameAr: c.nameAr, nameFr: c.nameFr, sortOrder: Number(c.sortOrder) || 0, minPrice: Number(c.minPrice) || 0, maxPrice: Number(c.maxPrice) || 0 };
      await api(edit ? '/admin/categories/' + initial.id : '/admin/categories', { method: edit ? 'PUT' : 'POST', body });
      toast(t('a_done')); onSaved();
    });
  };
  const f = (k, ph, extra) => <input className="inp" value={c[k]} placeholder={ph} aria-label={ph} onChange={(e) => setC({ ...c, [k]: e.target.value })} required {...extra} />;
  return (
    <form className="catform" onSubmit={save}>
      {f('code', t('a_code'), { disabled: edit, pattern: '[a-z0-9_-]+', dir: 'ltr' })}
      {f('nameAr', t('a_nameAr'), { dir: 'rtl' })}
      {f('nameFr', t('a_nameFr'))}
      {f('sortOrder', t('a_order'), { type: 'number', min: 0 })}
      {f('minPrice', t('a_minPrice'), { type: 'number', min: 0, title: t('a_minPrice') })}
      {f('maxPrice', t('a_maxPrice'), { type: 'number', min: 1, title: t('a_maxPrice') })}
      <span className="row g8"><button className="btn sm" type="submit">{t('a_save')}</button>{onCancel ? <button type="button" className="btn sm ghost" onClick={onCancel}>{t('a_cancel')}</button> : null}</span>
    </form>
  );
}

function Categories() {
  const st = useAsync(() => api('/admin/categories'), []);
  const [editing, setEditing] = useState(null);
  const [adding, setAdding] = useState(false);
  const done = () => { setEditing(null); setAdding(false); st.reload(); };
  return (
    <Gate st={st} loader={<Skeleton rows={5} />}>{(list) => (
      <>
        <div className="card tbl">
          {list.map((c) => (editing === c.id
            ? <div key={c.id} className="trow"><CategoryForm initial={c} onSaved={done} onCancel={() => setEditing(null)} /></div>
            : (
              <div key={c.id} className="trow">
                <span className="grow col" style={{ gap: 2 }}><b>{L(c, 'name')} <span className="muted xs" dir="ltr">({c.code})</span></b><span className="muted xs">{t('a_catUsed', { a: c.artisans, r: c.requests })} · {t('a_priceRange')}: {fmt(c.minPrice)} – {fmt(c.maxPrice)} {t('dh')}</span></span>
                <span className="row g8">
                  <button className="link" onClick={() => setEditing(c.id)}>{t('a_edit')}</button>
                  {c.artisans + c.requests === 0 ? <Confirm label={t('a_delete')} onYes={() => guarded(async () => { await api('/admin/categories/' + c.id, { method: 'DELETE' }); toast(t('a_done')); st.reload(); })} /> : null}
                </span>
              </div>
            )))}
        </div>
        {adding ? <div className="card"><CategoryForm onSaved={done} onCancel={() => setAdding(false)} /></div>
          : <button className="btn sm dark" style={{ alignSelf: 'flex-start' }} onClick={() => setAdding(true)}><Icon name="plus" size={16} />{t('a_add')}</button>}
      </>
    )}</Gate>
  );
}

const ACTIONS = ['LOGIN', 'LOGIN_FAILED', 'LOGIN_BLOCKED', 'REGISTER', 'REQUEST_CREATED', 'OFFER_SENT', 'OFFER_ACCEPTED', 'REQUEST_CANCELLED', 'JOB_DONE', 'REVIEW_ADDED',
  'USER_BLOCKED', 'USER_UNBLOCKED', 'PASSWORD_RESET', 'ROLE_CHANGED', 'ARTISAN_VERIFIED', 'ARTISAN_UNVERIFIED', 'REQUEST_CANCELLED_BY_ADMIN', 'REVIEW_DELETED',
  'CATEGORY_CREATED', 'CATEGORY_UPDATED', 'CATEGORY_DELETED', 'BROADCAST', 'SETTINGS_CHANGED'];

function AuditLine({ a }) {
  return (
    <div className="trow audit">
      <span className="grow col" style={{ gap: 2, minWidth: 0 }}>
        <span className="row g8 wrap"><span className="pill pill-mute mono">{a.action}</span><span className="muted xs">{when(a.createdAt)}</span></span>
        <span className="small" dir="auto">{a.actorName || '–'}{a.detail ? ' · ' + a.detail : ''}</span>
        {a.ip ? <span className="muted xs" dir="ltr" style={{ textAlign: 'start' }}>{a.ip}</span> : null}
      </span>
    </div>
  );
}

function Audit() {
  const [f, setF] = useState({ action: '', q: '', page: 0 });
  const [draft, setDraft] = useState('');
  const st = useAsync(() => api('/admin/audit?' + qs({ ...f, size: 25 })), [f.action, f.q, f.page]);
  return (
    <>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); setF((x) => ({ ...x, q: draft, page: 0 })); }}>
        <input className="inp" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={t('a_search')} aria-label={t('a_search')} />
        <select className="inp" value={f.action} onChange={(e) => setF((x) => ({ ...x, action: e.target.value, page: 0 }))}>
          <option value="">{t('a_allActions')}</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
      </form>
      <Gate st={st} loader={<Skeleton rows={6} />}>{(p) => (p.items.length ? (
        <>
          <div className="card tbl">{p.items.map((a) => <AuditLine key={a.id} a={a} />)}</div>
          <Pager page={p.page} size={p.size} total={p.total} onPage={(page) => setF((x) => ({ ...x, page }))} />
        </>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

async function download(path, name) {
  const res = await fetch('/api' + path, { headers: { Authorization: 'Bearer ' + getSession().token } });
  if (!res.ok) throw { code: 'GENERIC' };
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

// ------------------------------------------------------------ reports
function Reports() {
  const [status, setStatus] = useState('OPEN');
  const st = useAsync(() => api('/admin/reports?status=' + status), [status]);
  const [notes, setNotes] = useState({});
  const handle = (r, s) => guarded(async () => { await api('/admin/reports/' + r.id, { method: 'POST', body: { status: s, resolution: notes[r.id] || null } }); toast(t('a_done')); st.reload(); });
  return (
    <>
      <div className="row g6 wrap">{['OPEN', 'RESOLVED', 'DISMISSED'].map((s) => <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => setStatus(s)}>{t('rs_' + s)}</button>)}</div>
      <Gate st={st} loader={<Skeleton rows={4} />}>{(list) => (list.length ? (
        <div className="col g10">
          {list.map((r) => (
            <div key={r.id} className="card col g8">
              <div className="row between g8 wrap">
                <b>{t('rr_' + r.reason)}</b>
                <span className="muted xs">{when(r.createdAt)}</span>
              </div>
              <span className="small">
                {t('a_target')}: <a className="link" href={`#/admin/user/${r.targetUserId}`}>{r.targetName}</a> <span className="muted" dir="ltr">({r.targetPhone})</span>
                {r.reportsAgainstTarget > 1 ? <span className="pill pill-warn" style={{ marginInlineStart: 6 }}>{t('a_reportsN', { n: r.reportsAgainstTarget })}</span> : null}
              </span>
              <span className="muted xs">{t('a_reporter')}: {r.reporterName} <span dir="ltr">({r.reporterPhone})</span> · {r.targetType} #{r.targetId}</span>
              {r.details ? <p className="small" style={{ margin: 0 }} dir="auto">{r.details}</p> : null}
              {r.status === 'OPEN' ? (
                <>
                  <input className="inp" maxLength={500} value={notes[r.id] || ''} onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })} placeholder={t('a_resolution')} />
                  <div className="row g8 wrap">
                    <button className="btn sm" onClick={() => handle(r, 'RESOLVED')}>{t('a_resolve')}</button>
                    <button className="btn sm ghost" onClick={() => handle(r, 'DISMISSED')}>{t('a_dismiss')}</button>
                    <a className="btn sm ghost" href={`#/admin/user/${r.targetUserId}`}>{t('a_users')}</a>
                  </div>
                </>
              ) : (r.resolution ? <span className="muted xs">{r.resolution}</span> : null)}
            </div>
          ))}
        </div>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

// ------------------------------------------------------------ problems between clients and artisans
const STRIKE_REASONS = ['NO_SHOW', 'LATE_CANCEL', 'UNPAID', 'CLIENT_ABSENT', 'WARRANTY_IGNORED', 'BAD_WORK', 'BEHAVIOR', 'OTHER'];
const CASE_PILL = { OPEN: 'pill-warn', ESCALATED: 'pill-warn', FIXED: 'pill-lime', RESOLVED: 'pill-ok' };

function Cases() {
  const [status, setStatus] = useState('open');
  const st = useAsync(() => api('/admin/cases?status=' + status), [status], { live: true });
  return (
    <>
      <div className="row g6 wrap">{[['open', 'a_casesOpen'], ['all', 'a_casesAll']].map(([s, k]) => <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => setStatus(s)}>{t(k)}</button>)}</div>
      <Gate st={st} loader={<Skeleton rows={4} />}>{(list) => (list.length ? (
        <div className="col g10">
          {list.map((c) => (
            <a key={c.id} className="card col g6" href={`#/admin/case/${c.id}`} style={{ textDecoration: 'none' }}>
              <div className="row between g8 wrap">
                <b>{c.kind === 'WARRANTY' ? t('j_warranty') + ' · ' : ''}{t('pr_' + c.reason)}</b>
                <span className={`pill ${CASE_PILL[c.status] || 'pill-mute'}`}>{t('cs_' + c.status)}</span>
              </div>
              <span className="small">{catLabel(c)} · {c.clientName} ↔ {c.artisanName || '—'}</span>
              <span className="muted xs">{t('a_openedBy')}: {c.openedByName} ({t('a_role_' + c.openedRole)}) · {when(c.createdAt)} · #{c.requestId}</span>
              {c.details ? <span className="small" dir="auto" style={{ color: 'var(--ink-2)' }}>{c.details}</span> : null}
            </a>
          ))}
        </div>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

function StrikeList({ party, onChange }) {
  const [reason, setReason] = useState('');
  const add = () => guarded(async () => { await api('/admin/users/' + party.userId + '/strikes', { method: 'POST', body: { reason } }); setReason(''); toast(t('a_done')); onChange(); });
  const remove = (s) => guarded(async () => { await api('/admin/strikes/' + s.id, { method: 'DELETE' }); toast(t('a_done')); onChange(); });
  return (
    <div className="col g8">
      <span className="row g6 wrap">
        <span className={`pill ${party.recentStrikes ? 'pill-warn' : 'pill-ok'}`}>{t('a_strikesN', { n: party.recentStrikes })}</span>
        {party.suspendedUntil ? <span className="pill pill-warn">{t('a_suspendedUntil', { d: when(party.suspendedUntil) })}</span> : null}
      </span>
      {party.strikes.map((s) => (
        <div key={s.id} className="row between g8 small" style={{ opacity: s.removed ? 0.5 : 1 }}>
          <span>{t('sr_' + s.reason)}{s.automatic ? ' · ' + t('a_auto') : ''} · <span className="muted">{when(s.createdAt)}</span>{s.requestId ? <> · <a className="link" href={`#/admin/request/${s.requestId}`}>#{s.requestId}</a></> : null}</span>
          {s.removed ? null : <button className="link danger" onClick={() => remove(s)}>{t('a_removeStrike')}</button>}
        </div>
      ))}
      <div className="row g8 wrap">
        <select className="select" style={{ flex: '1 1 180px', width: 'auto' }} value={reason} onChange={(e) => setReason(e.target.value)}>
          <option value="">{t('a_addStrike')}…</option>
          {STRIKE_REASONS.map((r) => <option key={r} value={r}>{t('sr_' + r)}</option>)}
        </select>
        <button className="btn sm ghost" disabled={!reason} onClick={add}>{t('a_addStrike')}</button>
      </div>
    </div>
  );
}

function UserStrikes({ userId }) {
  const st = useAsync(() => api('/admin/users/' + userId + '/strikes'), [userId]);
  return (
    <div className="card col g10">
      <b>{t('a_strikes')}</b>
      <Gate st={st} loader={<Skeleton rows={1} />}>{(p) => <StrikeList party={p} onChange={st.reload} />}</Gate>
    </div>
  );
}

function CaseDetail({ id }) {
  const st = useAsync(() => api('/admin/cases/' + id), [id]);
  const [note, setNote] = useState('');
  const [sc, setSc] = useState('');
  const [sa, setSa] = useState('');
  const [close, setClose] = useState(false);
  const handle = (status) => guarded(async () => {
    await api('/admin/cases/' + id, { method: 'POST', body: { status, resolution: note || null, strikeClient: sc || null, strikeArtisan: sa || null, closeJob: close } });
    toast(t('a_done'));
    setSc(''); setSa(''); setClose(false);
    st.reload();
  });
  return (
    <Gate st={st} loader={<Skeleton rows={6} />}>{(d) => {
      const c = d.row;
      const open = c.status !== 'RESOLVED';
      const party = (p, role) => (p ? (
        <div className="card col g8">
          <div className="row between g8 wrap">
            <b>{t('a_role_' + role)} · <a className="link" href={`#/admin/user/${p.userId}`}>{p.name}</a></b>
            <a className="btn sm ghost" href={`tel:${p.phone}`} dir="ltr"><Icon name="phone" size={16} />{p.phone}</a>
          </div>
          <StrikeList party={p} onChange={st.reload} />
        </div>
      ) : null);
      const timeline = [['a_t_appt', d.acceptedAppointment ? when(d.acceptedAppointment) + (d.appointmentConfirmed ? ' ✓' : '') : null],
        ['a_t_arrived', d.arrivedAt && when(d.arrivedAt)], ['a_t_finished', d.finishedAt && when(d.finishedAt)],
        ['a_t_done', d.doneAt && when(d.doneAt) + ' · ' + t('j_doneBy_' + d.doneBy)], ['a_t_warranty', d.warrantyUntil && when(d.warrantyUntil)]];
      const extras = d.items.filter((i) => i.kind === 'EXTRA' && i.status === 'ACCEPTED').reduce((a, i) => a + i.amount, 0);
      const advances = d.items.filter((i) => i.kind === 'ADVANCE' && i.status === 'ACCEPTED').reduce((a, i) => a + i.amount, 0);
      return (
        <div className="col g12">
          <div className="card col g8">
            <div className="row between g8 wrap">
              <b style={{ fontSize: 18 }}>{c.kind === 'WARRANTY' ? t('j_warranty') + ' · ' : ''}{t('pr_' + c.reason)}</b>
              <span className={`pill ${CASE_PILL[c.status] || 'pill-mute'}`}>{t('cs_' + c.status)}</span>
            </div>
            <span className="small">{t('a_openedBy')}: {c.openedByName} ({t('a_role_' + c.openedRole)}) · {when(c.createdAt)}</span>
            {c.details ? <p className="small" dir="auto" style={{ margin: 0 }}>{c.details}</p> : null}
            {d.mediaUrl ? <a href={d.mediaUrl} target="_blank" rel="noreferrer"><img src={d.mediaUrl} alt="" style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 12 }} /></a> : null}
            {d.resolution ? <div className="note ok"><span dir="auto">{d.resolution}</span></div> : null}
          </div>

          <div className="cols2">
            {party(d.client, 'CLIENT')}
            {party(d.artisan, 'ARTISAN')}
          </div>

          <div className="card col g8">
            <b>{t('a_caseDeal')}</b>
            <span className="small">{catLabel(c)} · {d.city}{d.address ? ' · ' + d.address : ''} · <a className="link" href={`#/admin/request/${c.requestId}`}>#{c.requestId}</a></span>
            <p className="small muted" dir="auto" style={{ margin: 0 }}>{d.description}</p>
            <div className="dl-list small num">
              <span>{t('j_agreed')}: <b>{money(d.agreedPrice)}</b></span>
              <span>{t('j_extras')}: <b>+{money(extras)}</b></span>
              <span>{t('j_advances')}: <b>−{money(advances)}</b></span>
              <span>{t('j_remaining')}: <b>{money(d.agreedPrice + extras - advances)}</b></span>
            </div>
            {d.items.map((i) => <span key={i.id} className="xs muted">{t('jk_' + i.kind)} · {money(i.amount)}{i.label ? ' · ' + i.label : ''} · {t('a_role_' + i.createdBy)} · {t('is_' + i.status)} · {when(i.createdAt)}{i.mediaUrl ? <> · <a className="link" href={i.mediaUrl} target="_blank" rel="noreferrer">📎</a></> : null}</span>)}
          </div>

          <div className="card col g6">
            <b>{t('a_timeline')}</b>
            {timeline.map(([k, v]) => <span key={k} className="small">{t(k)}: <b>{v || '—'}</b></span>)}
          </div>

          {d.requestMedia.length || d.before.length || d.after.length ? (
            <div className="card col g8">
              {d.requestMedia.length ? <><b className="small">{t('a_requestMedia')}</b><MediaGallery media={d.requestMedia} /></> : null}
              {d.before.length ? <><b className="small">{t('j_before')}</b><MediaGallery media={d.before} /></> : null}
              {d.after.length ? <><b className="small">{t('j_after')}</b><MediaGallery media={d.after} /></> : null}
            </div>
          ) : null}

          {d.otherCases.length ? (
            <div className="card col g6">
              <b className="small">{t('a_cases')}</b>
              {d.otherCases.map((o) => <a key={o.id} className="small link" href={`#/admin/case/${o.id}`}>{t('pr_' + o.reason)} · {t('a_role_' + o.openedRole)} · {t('cs_' + o.status)}</a>)}
            </div>
          ) : null}

          <div className="card col g6">
            <b>{t('a_chatLog')}</b>
            {d.chat.length ? d.chat.map((m, i) => <span key={i} className="small" dir="auto"><b>{t('a_role_' + m.from)}</b> · <span className="muted xs">{when(m.at)}</span><br />{m.body}</span>) : <span className="muted small">—</span>}
          </div>

          <div className="card col g10">
            <span className="note wait"><Icon name="phone" size={18} />{t('a_callBoth')}</span>
            <textarea className="area" rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('a_resolution')} />
            <div className="row g8 wrap">
              <label className="col g4 grow"><span className="xs muted">{t('a_strikeClient')}</span>
                <select className="select" value={sc} onChange={(e) => setSc(e.target.value)}><option value="">{t('a_none')}</option>{STRIKE_REASONS.map((r) => <option key={r} value={r}>{t('sr_' + r)}</option>)}</select>
              </label>
              <label className="col g4 grow"><span className="xs muted">{t('a_strikeArtisan')}</span>
                <select className="select" value={sa} onChange={(e) => setSa(e.target.value)}><option value="">{t('a_none')}</option>{STRIKE_REASONS.map((r) => <option key={r} value={r}>{t('sr_' + r)}</option>)}</select>
              </label>
            </div>
            {d.requestStatus === 'ACCEPTED' ? <label className="row g8 small"><input type="checkbox" checked={close} onChange={(e) => setClose(e.target.checked)} />{t('a_closeJob')}</label> : null}
            <div className="row g8 wrap">
              <button className="btn sm" onClick={() => handle('RESOLVED')}>{t('a_resolve')}</button>
              {open ? <button className="btn sm ghost" onClick={() => handle('OPEN')}>{t('a_keepOpen')}</button> : null}
            </div>
          </div>
        </div>
      );
    }}</Gate>
  );
}

// ------------------------------------------------------------ paid plans
function ArtisanPlan({ artisanId }) {
  const st = useAsync(() => api('/admin/artisans/' + artisanId + '/plan'), [artisanId]);
  const [f, setF] = useState({ kind: 'SUBSCRIPTION', months: 1, amount: '', method: 'CASHPLUS', note: '' });
  const save = (e) => {
    e.preventDefault();
    guarded(async () => {
      await api('/admin/artisans/' + artisanId + '/payments', { method: 'POST', body: { ...f, months: Number(f.months) || 1, amount: Number(f.amount) || 0 } });
      toast(t('a_done'));
      setF({ ...f, amount: '', note: '' });
      st.reload();
    });
  };
  return (
    <Gate st={st} loader={<Skeleton rows={1} />}>{(p) => (
      <div className="card col g10">
        <b className="row g6"><Icon name="crown" size={16} />{t('planTitle')}</b>
        <div className="kvs">
          <Field k={t('a_subscription')} v={p.monetization ? (p.active ? t('planActive') : t('planExpired')) : t('planFree')} />
          {p.subscriptionUntil ? <Field k={t('a_until')} v={when(p.subscriptionUntil)} /> : null}
          <Field k={t('featured')} v={p.featured ? when(p.featuredUntil) : '–'} />
        </div>
        <form className="row g8 wrap" onSubmit={save}>
          <select className="inp" value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>
            <option value="SUBSCRIPTION">{t('pk_SUBSCRIPTION')}</option><option value="FEATURED">{t('pk_FEATURED')}</option>
          </select>
          <select className="inp" value={f.months} onChange={(e) => setF({ ...f, months: e.target.value })}>
            {[1, 2, 3, 6, 12].map((m) => <option key={m} value={m}>{m} {t('months')}</option>)}
          </select>
          <input className="inp" inputMode="numeric" value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value.replace(/[^\d]/g, '') })} placeholder={t('a_amount')} required />
          <select className="inp" value={f.method} onChange={(e) => setF({ ...f, method: e.target.value })}>
            {['CASHPLUS', 'VIREMENT', 'WAFACASH', 'CASH', 'OTHER'].map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <input className="inp" maxLength={300} value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} placeholder={t('a_note')} />
          <button className="btn sm" type="submit">{t('a_recordPayment')}</button>
        </form>
        {p.payments.length ? p.payments.map((x) => <div key={x.id} className="row between g8 line small"><span>{t('pk_' + x.kind)} · {x.months} {t('months')} · {x.method}</span><span className="num">{fmt(x.amount)} {t('dh')} · {when(x.createdAt)}</span></div>) : null}
      </div>
    )}</Gate>
  );
}

function Monetization() {
  const st = useAsync(() => api('/admin/monetization'), []);
  const pays = useAsync(() => api('/admin/payments'), []);
  const [m, setM] = useState(null);
  const v = m || st.data;
  if (!v) return null;
  const save = (e) => {
    e.preventDefault();
    guarded(async () => {
      await api('/admin/monetization', { method: 'PUT', body: { ...v, subscriptionPrice: Number(v.subscriptionPrice) || 0, trialDays: Number(v.trialDays) || 0, featuredPrice: Number(v.featuredPrice) || 0 } });
      toast(t('a_done')); setM(null); st.reload();
    });
  };
  const num = (k) => <input className="inp" inputMode="numeric" value={v[k]} onChange={(e) => setM({ ...v, [k]: e.target.value.replace(/[^\d]/g, '') })} />;
  return (
    <form className="card col g12" onSubmit={save}>
      <b className="row g6"><Icon name="crown" size={16} />{t('a_monetization')}</b>
      <label className="row between g12"><span>{t('a_monetizationOn')}</span><input type="checkbox" checked={v.enabled} onChange={(e) => setM({ ...v, enabled: e.target.checked })} style={{ width: 22, height: 22, accentColor: '#0E2B22' }} /></label>
      <div className="grid2c">
        <label className="col g6"><span className="muted small">{t('a_subPrice')}</span>{num('subscriptionPrice')}</label>
        <label className="col g6"><span className="muted small">{t('a_trialDays')}</span>{num('trialDays')}</label>
        <label className="col g6"><span className="muted small">{t('a_featuredPrice')}</span>{num('featuredPrice')}</label>
      </div>
      <label className="col g6"><span className="muted small">{t('a_payInstructions')}</span>
        <textarea className="inp" rows={3} maxLength={450} value={v.paymentInstructions || ''} onChange={(e) => setM({ ...v, paymentInstructions: e.target.value })} /></label>
      <button className="btn sm" type="submit" style={{ alignSelf: 'flex-start' }}>{t('a_save')}</button>
      {pays.data && pays.data.length ? (
        <div className="col g6"><b className="small">{t('planPayments')}</b>
          {pays.data.slice(0, 20).map((x) => <div key={x.id} className="row between g8 line small"><a className="link" href={`#/admin/file/${x.artisanId}`}>{x.artisanName}</a><span className="num">{t('pk_' + x.kind)} · {fmt(x.amount)} {t('dh')} · {when(x.createdAt)}</span></div>)}
        </div>
      ) : null}
    </form>
  );
}

function Tools() {
  const settings = useAsync(() => api('/admin/settings'), []);
  const sys = useAsync(() => api('/admin/system'), []);
  const [aud, setAud] = useState('ALL');
  const [msg, setMsg] = useState('');
  const [edit, setEdit] = useState(null);
  const s = edit || settings.data;
  const hms = (sec) => `${Math.floor(sec / 86400)}d ${Math.floor((sec % 86400) / 3600)}h ${Math.floor((sec % 3600) / 60)}m`;
  return (
    <>
      {can('BILLING') ? <Monetization /> : null}
      {can('BROADCAST') ? <form className="card col g12" onSubmit={(e) => { e.preventDefault(); guarded(async () => { const r = await api('/admin/broadcast', { method: 'POST', body: { audience: aud, message: msg } }); toast(t('a_sent', { n: r.sent })); setMsg(''); }); }}>
        <b>{t('a_broadcast')}</b>
        <select className="inp" value={aud} onChange={(e) => setAud(e.target.value)} aria-label={t('a_audience')}>
          <option value="ALL">{t('a_aAll')}</option><option value="CLIENTS">{t('a_aClients')}</option><option value="ARTISANS">{t('a_aArtisans')}</option>
        </select>
        <textarea className="inp" rows={3} maxLength={160} value={msg} onChange={(e) => setMsg(e.target.value)} placeholder={t('a_message')} required />
        <button className="btn sm" type="submit" disabled={!msg.trim()} style={{ alignSelf: 'flex-start' }}>{t('a_send')}</button>
      </form> : null}

      {s && can('SETTINGS') ? (
        <form className="card col g12" onSubmit={(e) => { e.preventDefault(); guarded(async () => { const r = await api('/admin/settings', { method: 'PUT', body: { registrationsOpen: s.registrationsOpen, bannerMessage: s.bannerMessage || '' } }); setEdit(null); settings.reload(); toast(t('a_done')); return r; }); }}>
          <b>{t('a_settings')}</b>
          <label className="row between g12"><span>{t('a_regOpen')}</span><input type="checkbox" checked={s.registrationsOpen} onChange={(e) => setEdit({ ...s, registrationsOpen: e.target.checked })} style={{ width: 22, height: 22, accentColor: '#0E2B22' }} /></label>
          <label className="col g8"><span className="muted small">{t('a_banner')}</span>
            <input className="inp" maxLength={300} value={s.bannerMessage || ''} onChange={(e) => setEdit({ ...s, bannerMessage: e.target.value })} /></label>
          <button className="btn sm" type="submit" style={{ alignSelf: 'flex-start' }}>{t('a_save')}</button>
        </form>
      ) : null}

      {sys.data && can('SETTINGS') ? (
        <div className="card col g8">
          <b>{t('a_system')}</b>
          <div className="kvs">
            <Field k={t('a_db')} v={sys.data.databaseOk ? t('a_ok') : t('a_ko')} />
            <Field k={t('a_uptime')} v={hms(sys.data.uptimeSeconds)} />
            <Field k={t('a_memory')} v={`${sys.data.heapUsedMb} / ${sys.data.heapMaxMb} MB`} />
            <Field k={t('a_java')} v={sys.data.javaVersion} />
            <Field k={t('a_cpu')} v={sys.data.processors} />
            <Field k={t('a_live')} v={sys.data.liveConnections} />
          </div>
        </div>
      ) : null}

      {can('USERS') || can('REQUESTS') ? (
        <div className="card col g8">
          <b>{t('a_export')}</b>
          <div className="row g8 wrap">
            {can('USERS') ? <button className="btn sm ghost" onClick={() => guarded(() => download('/admin/export/users.csv', 'users.csv'))}>{t('a_exportUsers')}</button> : null}
            {can('REQUESTS') ? <button className="btn sm ghost" onClick={() => guarded(() => download('/admin/export/requests.csv', 'requests.csv'))}>{t('a_exportReq')}</button> : null}
          </div>
        </div>
      ) : null}
    </>
  );
}

// ------------------------------------------------------------ artisan activation files
const FILE_STATUSES = ['PENDING', 'REJECTED', 'DRAFT', 'APPROVED'];

function Files() {
  const [status, setStatus] = useState('PENDING');
  const st = useAsync(() => api('/admin/artisan-files?status=' + status), [status]);
  return (
    <>
      <div className="row g6 wrap">{FILE_STATUSES.map((s) => <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => setStatus(s)}>{t('a_st_' + s)}</button>)}</div>
      <Gate st={st} loader={<Skeleton rows={4} />}>{(list) => (list.length ? (
        <div className="card tbl">
          {list.map((a) => (
            <a key={a.artisanId} className="trow" href={`#/admin/file/${a.artisanId}`}>
              <span className="grow col" style={{ gap: 2, minWidth: 0 }}>
                <b className="ell">{a.fullName}</b>
                <span className="muted xs"><span dir="ltr">{a.phone}</span> · {getSession().lang === 'ar' ? a.categoryNameAr : a.categoryNameFr} · {a.city}</span>
              </span>
              <span className="col" style={{ alignItems: 'flex-end', gap: 4 }}>
                <span className={`pill ${a.status === 'APPROVED' ? 'pill-ok' : a.status === 'PENDING' ? 'pill-lime' : 'pill-warn'}`}>{t('a_st_' + a.status)}</span>
                {a.submittedAt ? <span className="muted xs">{t('a_submitted')} {ago(a.submittedAt)}</span> : null}
              </span>
            </a>
          ))}
        </div>
      ) : <Empty>{t('a_noResults')}</Empty>)}</Gate>
    </>
  );
}

function FileDetail({ id }) {
  const st = useAsync(() => api('/admin/artisan-files/' + id), [id]);
  const cats = useAsync(() => api('/categories'), []);
  const [reason, setReason] = useState('');
  const [big, setBig] = useState(null);
  const decide = (path, body) => guarded(async () => { await api(`/admin/artisan-files/${id}/${path}`, { method: 'POST', body }); toast(t('a_done')); st.reload(); });
  return (
    <Gate st={st} loader={<Skeleton rows={5} />}>{(d) => {
      const f = d.file;
      const cat = (cats.data || []).find((c) => c.code === f.categoryCode);
      return (
        <>
          <div className="card col g12">
            <div className="row g12">
              {f.avatar ? <img className="avatar" style={{ width: 64, height: 64, borderRadius: 32, objectFit: 'cover', cursor: 'zoom-in' }} src={f.avatar.url} alt="" onClick={() => setBig(f.avatar.url)} /> : <div className="avatar">?</div>}
              <div className="col grow" style={{ gap: 2, minWidth: 0 }}>
                <b style={{ fontSize: 19 }}>{f.fullName}</b>
                <span className="muted small">{cat ? L(cat, 'name') : f.categoryCode} · {f.yearsExperience} {t('obYears').toLowerCase()}</span>
              </div>
              <span className={`pill ${f.status === 'APPROVED' ? 'pill-ok' : f.status === 'PENDING' ? 'pill-lime' : 'pill-warn'}`}>{t('a_st_' + f.status)}</span>
            </div>
            {f.status === 'REJECTED' && f.rejectionReason ? <div className="note wait">{f.rejectionReason}</div> : null}
            <div className="kvs">
              <Field k={t('a_phone')} v={<span dir="ltr">{d.phone}</span>} />
              <Field k={t('obCin')} v={<span dir="ltr">{f.cin || '–'}</span>} />
              <Field k={t('obWhatsapp')} v={<span dir="ltr">{f.whatsapp || '–'}</span>} />
              <Field k={t('a_joined')} v={when(d.createdAt)} />
              {d.email ? <Field k="E-mail" v={<span dir="ltr">{d.email}</span>} /> : null}
              {d.submittedAt ? <Field k={t('a_submitted')} v={when(d.submittedAt)} /> : null}
            </div>
            <div><span className="muted small">{t('obBio').split('(')[0]}</span><p style={{ margin: '4px 0 0' }} dir="auto">{f.bio || '–'}</p></div>
          </div>

          <div className="card col g8">
            <b>{t('obIdentity')}</b>
            <div className="grid2c">
              {[['idFront', 'obIdFront'], ['idBack', 'obIdBack']].map(([k, label]) => (
                <div key={k} className="col g6"><span className="muted xs">{t(label)}</span>
                  {f[k] ? <div className="islot has"><PrivateImg src={f[k].url} onClick={(u) => setBig(u)} /></div> : <div className="islot">–</div>}</div>
              ))}
            </div>
          </div>

          <div className="card col g8">
            <b>{t('obZone')}</b>
            <span className="small">{[f.address, f.city].filter(Boolean).join(' · ') || '–'} · {t('obRadius', { n: f.radiusKm })}</span>
            {f.lat != null ? <MapPreview lat={f.lat} lng={f.lng} radiusKm={f.radiusKm} height={200} /> : null}
          </div>

          <div className="card col g8">
            <b>{t('obPortfolio')}</b>
            {f.portfolio.length ? <div className="mgrid">{f.portfolio.map((m) => <button key={m.id} type="button" className="mthumb" onClick={() => setBig(m.url)}><img src={m.url} alt="" /></button>)}</div> : <span className="muted small">–</span>}
          </div>

          {f.status === 'PENDING' ? (
            <div className="card col g12">
              <button className="btn" onClick={() => decide('approve')}><Icon name="checkMark" size={18} />{t('a_approve')}</button>
              <Confirm label={t('a_reject')} onYes={() => decide('reject', { reason })}>
                <input className="inp" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} placeholder={t('a_rejectReason')} />
              </Confirm>
            </div>
          ) : null}
          {f.missing && f.missing.length && f.status !== 'APPROVED' ? <span className="muted xs">{t('obMissing')}{f.missing.map((k) => t('m_' + k)).join(', ')}</span> : null}
          {big ? <Lightbox src={big} onClose={() => setBig(null)} /> : null}
        </>
      );
    }}</Gate>
  );
}

// ------------------------------------------------------------ team (owner only)
const PERMS = ['FILES', 'USERS', 'REQUESTS', 'REPORTS', 'CATEGORIES', 'BILLING', 'BROADCAST', 'SETTINGS', 'AUDIT'];

function PermPicker({ value, onChange }) {
  const toggle = (p) => onChange(value.includes(p) ? value.filter((x) => x !== p) : [...value, p]);
  return (
    <div className="permgrid">
      {PERMS.map((p) => (
        <label key={p} className={`perm ${value.includes(p) ? 'on' : ''}`}>
          <input type="checkbox" checked={value.includes(p)} onChange={() => toggle(p)} />
          <span className="col" style={{ gap: 0 }}><b>{t('perm_' + p)}</b><small className="muted">{t('permd_' + p)}</small></span>
        </label>
      ))}
    </div>
  );
}

function StaffCard({ s, reload }) {
  const [perms, setPerms] = useState(s.permissions);
  const [temp, setTemp] = useState(null);
  const changed = perms.slice().sort().join() !== s.permissions.slice().sort().join();
  const act = (path, opts) => guarded(async () => { const r = await api('/admin/staff/' + s.id + path, opts || { method: 'POST' }); toast(t('a_done')); reload(); return r; });
  return (
    <div className="card col g10">
      <div className="row between g8 wrap">
        <span className="col" style={{ gap: 0 }}><b>{s.fullName}</b><span className="muted xs" dir="ltr" style={{ textAlign: 'start' }}>{s.phone}</span></span>
        <span className="row g6">
          {s.owner ? <span className="pill pill-lime">{t('a_owner')}</span> : null}
          {s.blocked ? <span className="pill pill-warn">{t('a_blockedS')}</span> : null}
          <span className="muted xs">{t('a_lastLogin')}: {s.lastLoginAt ? when(s.lastLoginAt) : t('a_never')}</span>
        </span>
      </div>
      {s.owner ? <span className="muted small">{t('a_ownerNote')}</span> : (
        <>
          <PermPicker value={perms} onChange={setPerms} />
          <div className="row g8 wrap">
            <button className="btn sm" disabled={!changed} onClick={() => act('', { method: 'PUT', body: { permissions: perms } })}>{t('a_save')}</button>
            <button className="btn sm ghost" onClick={() => act(s.blocked ? '/unblock' : '/block')}>{s.blocked ? t('a_unblock') : t('a_suspend')}</button>
            <Confirm label={t('a_reset')} onYes={() => guarded(async () => { const r = await api('/admin/staff/' + s.id + '/reset-password', { method: 'POST' }); setTemp(r.password); })} />
            <Confirm label={t('a_demote')} onYes={() => act('/demote')} />
            <Confirm label={t('a_delete')} danger onYes={() => act('', { method: 'DELETE' })} />
          </div>
          {temp ? <div className="note ok"><div className="small">{t('a_tempPass')}</div><code className="temp" dir="ltr">{temp}</code></div> : null}
        </>
      )}
    </div>
  );
}

function Team() {
  const st = useAsync(() => api('/admin/staff'), []);
  const [f, setF] = useState({ fullName: '', phone: '', password: '', permissions: ['FILES'] });
  const [open, setOpen] = useState(false);
  const create = (e) => {
    e.preventDefault();
    guarded(async () => {
      await api('/admin/staff', { method: 'POST', body: f });
      toast(t('a_done'));
      setF({ fullName: '', phone: '', password: '', permissions: ['FILES'] });
      setOpen(false);
      st.reload();
    });
  };
  return (
    <>
      <span className="muted small">{t('a_teamIntro')}</span>
      {open ? (
        <form className="card col g10" onSubmit={create}>
          <b>{t('a_newStaff')}</b>
          <div className="grid2c">
            <input className="inp" required maxLength={120} placeholder={t('fullName')} value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} />
            <input className="inp" required type="tel" dir="ltr" placeholder={t('phonePh')} value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
          </div>
          <input className="inp" required minLength={8} type="text" dir="ltr" placeholder={t('a_staffPassword')} value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
          <PermPicker value={f.permissions} onChange={(p) => setF({ ...f, permissions: p })} />
          <div className="row g8"><button className="btn sm" type="submit">{t('a_create')}</button><button className="btn sm ghost" type="button" onClick={() => setOpen(false)}>{t('a_cancel')}</button></div>
        </form>
      ) : <button className="btn sm dark" style={{ alignSelf: 'flex-start' }} onClick={() => setOpen(true)}><Icon name="plus" size={16} />{t('a_newStaff')}</button>}
      <Gate st={st} loader={<Skeleton rows={3} />}>{(list) => <div className="col g10">{list.map((s) => <StaffCard key={s.id + ':' + s.permissions.join()} s={s} reload={st.reload} />)}</div>}</Gate>
    </>
  );
}

// ------------------------------------------------------------ page
export default function Admin({ route }) {
  useSession();
  const sec = route.id || '';
  const detail = sec === 'user' || sec === 'request' || sec === 'file' || sec === 'case';
  let body;
  if (!isAdmin()) body = <Empty>{t('a_forbidden')}</Empty>;
  else if (sec === 'files') body = <Files />;
  else if (sec === 'reports') body = <Reports />;
  else if (sec === 'cases') body = <Cases />;
  else if (sec === 'case') body = <CaseDetail id={route.sub} />;
  else if (sec === 'file') body = <FileDetail id={route.sub} />;
  else if (sec === 'users') body = <Users />;
  else if (sec === 'user') body = <UserDetail id={route.sub} />;
  else if (sec === 'requests') body = <Requests />;
  else if (sec === 'request') body = <RequestDetail id={route.sub} />;
  else if (sec === 'reviews') body = <Reviews />;
  else if (sec === 'categories') body = <Categories />;
  else if (sec === 'audit') body = <Audit />;
  else if (sec === 'tools') body = <Tools />;
  else if (sec === 'team') body = <Team />;
  else body = <Dashboard />;
  const sectionOk = (SECTIONS.find(([id]) => id === (ALLOWED[sec] || sec)) || SECTIONS[0])[2]();
  if (isAdmin() && !sectionOk) body = <Empty>{t('a_noPermission')}</Empty>;
  const active = sec === 'user' ? 'users' : sec === 'request' ? 'requests' : sec === 'file' ? 'files' : sec === 'case' ? 'cases' : sec;
  return (
    <Screen tab="admin" width="wide">
      <div className="row g12">
        {detail ? <BackButton onClick={() => { location.hash = sec === 'user' ? '#/admin/users' : sec === 'file' ? '#/admin/files' : sec === 'case' ? '#/admin/cases' : '#/admin/requests'; }} /> : null}
        <h1 className="h1" style={{ fontSize: 24 }}><Icon name="shield" size={22} /> {t('a_title')}</h1>
      </div>
      <nav className="atabs" aria-label={t('a_title')}>
        {SECTIONS.filter(([, , ok]) => ok()).map(([id, key]) => <a key={id} href={`#/admin${id ? '/' + id : ''}`} className={active === id ? 'on' : ''}>{t(key)}</a>)}
      </nav>
      {body}
    </Screen>
  );
}
