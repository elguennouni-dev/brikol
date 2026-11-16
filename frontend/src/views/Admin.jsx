import { useState } from 'react';
import { api, useAsync, useSession, isAdmin, guarded, toast, t, L, fmt, ago, getSession } from '../session';
import { RequestPlace, MapPreview } from '../places';
import { MediaGallery, PrivateImg, Lightbox } from '../media';
import { Screen, Icon, Gate, Skeleton, Empty, BackButton } from '../ui';

const SECTIONS = [['', 'a_dash'], ['files', 'a_files'], ['users', 'a_users'], ['requests', 'a_requests'], ['reviews', 'a_reviews'], ['categories', 'a_cats'], ['audit', 'a_audit'], ['tools', 'a_tools']];
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
        {s.pendingArtisans ? (
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
              {u.artisanId ? <a className="btn sm ghost" href={`#/admin/file/${u.artisanId}`}><Icon name="doc" size={16} />{t('a_files')}</a> : null}
              {u.artisanId ? (
                <button className="btn sm ghost" onClick={() => act(() => api(`/admin/artisans/${u.artisanId}/verified`, { method: 'POST', body: { verified: !u.verified } }))}>
                  <Icon name="checkMark" size={16} />{u.verified ? t('a_unverify') : t('a_verify')}
                </button>
              ) : (
                <Confirm label={u.role === 'ADMIN' ? t('a_makeClient') : t('a_makeAdmin')} onYes={() => act(() => api(`/admin/users/${u.id}/role`, { method: 'POST', body: { role: u.role === 'ADMIN' ? 'CLIENT' : 'ADMIN' } }))} />
              )}
              <Confirm label={t('a_reset')} onYes={() => guarded(async () => { const r = await api(`/admin/users/${u.id}/reset-password`, { method: 'POST' }); setTemp(r.password); })} />
              {temp ? <div className="note ok"><div className="small">{t('a_tempPass')}</div><code className="temp" dir="ltr">{temp}</code></div> : null}
            </div>
          )}

          {d.recentRequests.length ? (
            <div className="card col g8">
              <b>{t('a_recentReq')}</b>
              {d.recentRequests.map((r) => <RequestLine key={r.id} r={r} />)}
            </div>
          ) : null}
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
      <form className="card col g12" onSubmit={(e) => { e.preventDefault(); guarded(async () => { const r = await api('/admin/broadcast', { method: 'POST', body: { audience: aud, message: msg } }); toast(t('a_sent', { n: r.sent })); setMsg(''); }); }}>
        <b>{t('a_broadcast')}</b>
        <select className="inp" value={aud} onChange={(e) => setAud(e.target.value)} aria-label={t('a_audience')}>
          <option value="ALL">{t('a_aAll')}</option><option value="CLIENTS">{t('a_aClients')}</option><option value="ARTISANS">{t('a_aArtisans')}</option>
        </select>
        <textarea className="inp" rows={3} maxLength={160} value={msg} onChange={(e) => setMsg(e.target.value)} placeholder={t('a_message')} required />
        <button className="btn sm" type="submit" disabled={!msg.trim()} style={{ alignSelf: 'flex-start' }}>{t('a_send')}</button>
      </form>

      {s ? (
        <form className="card col g12" onSubmit={(e) => { e.preventDefault(); guarded(async () => { const r = await api('/admin/settings', { method: 'PUT', body: { registrationsOpen: s.registrationsOpen, bannerMessage: s.bannerMessage || '' } }); setEdit(null); settings.reload(); toast(t('a_done')); return r; }); }}>
          <b>{t('a_settings')}</b>
          <label className="row between g12"><span>{t('a_regOpen')}</span><input type="checkbox" checked={s.registrationsOpen} onChange={(e) => setEdit({ ...s, registrationsOpen: e.target.checked })} style={{ width: 22, height: 22, accentColor: '#0E2B22' }} /></label>
          <label className="col g8"><span className="muted small">{t('a_banner')}</span>
            <input className="inp" maxLength={300} value={s.bannerMessage || ''} onChange={(e) => setEdit({ ...s, bannerMessage: e.target.value })} /></label>
          <button className="btn sm" type="submit" style={{ alignSelf: 'flex-start' }}>{t('a_save')}</button>
        </form>
      ) : null}

      {sys.data ? (
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

      <div className="card col g8">
        <b>{t('a_export')}</b>
        <div className="row g8 wrap">
          <button className="btn sm ghost" onClick={() => guarded(() => download('/admin/export/users.csv', 'users.csv'))}>{t('a_exportUsers')}</button>
          <button className="btn sm ghost" onClick={() => guarded(() => download('/admin/export/requests.csv', 'requests.csv'))}>{t('a_exportReq')}</button>
        </div>
      </div>
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

// ------------------------------------------------------------ page
export default function Admin({ route }) {
  useSession();
  const sec = route.id || '';
  const detail = sec === 'user' || sec === 'request' || sec === 'file';
  let body;
  if (!isAdmin()) body = <Empty>{t('a_forbidden')}</Empty>;
  else if (sec === 'files') body = <Files />;
  else if (sec === 'file') body = <FileDetail id={route.sub} />;
  else if (sec === 'users') body = <Users />;
  else if (sec === 'user') body = <UserDetail id={route.sub} />;
  else if (sec === 'requests') body = <Requests />;
  else if (sec === 'request') body = <RequestDetail id={route.sub} />;
  else if (sec === 'reviews') body = <Reviews />;
  else if (sec === 'categories') body = <Categories />;
  else if (sec === 'audit') body = <Audit />;
  else if (sec === 'tools') body = <Tools />;
  else body = <Dashboard />;
  const active = sec === 'user' ? 'users' : sec === 'request' ? 'requests' : sec === 'file' ? 'files' : sec;
  return (
    <Screen tab="admin" width="wide">
      <div className="row g12">
        {detail ? <BackButton onClick={() => { location.hash = sec === 'user' ? '#/admin/users' : sec === 'file' ? '#/admin/files' : '#/admin/requests'; }} /> : null}
        <h1 className="h1" style={{ fontSize: 24 }}><Icon name="shield" size={22} /> {t('a_title')}</h1>
      </div>
      <nav className="atabs" aria-label={t('a_title')}>
        {SECTIONS.map(([id, key]) => <a key={id} href={`#/admin${id ? '/' + id : ''}`} className={active === id ? 'on' : ''}>{t(key)}</a>)}
      </nav>
      {body}
    </Screen>
  );
}
