import { useState } from 'react';
import { RequestPlace } from '../places';
import { MediaGallery } from '../media';
import { CounterForm } from '../price';
import { fmt } from '../session';
import { api, useAsync, guarded, toast, t, L, money, ago, etaLabel } from '../session';
import { Icon, Avatar, SegLinks, Screen, Gate, Empty, Skeleton, PageHead, catColors } from '../ui';

function ReviewForm({ r, onDone }) {
  const [rating, setRating] = useState(5);
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      await api('/requests/' + r.id + '/review', { method: 'POST', body: { rating, body: body || null } });
      toast(t('thanks'));
      onDone();
    }).then(() => setBusy(false));
  };
  return (
    <form onSubmit={submit} className="col g10">
      <b style={{ fontSize: 14 }}>{t('rateTitle')}</b>
      <div className="stars">
        {[5, 4, 3, 2, 1].map((n) => (
          <span key={n} style={{ display: 'contents' }}>
            <input type="radio" id={`st${r.id}-${n}`} name={`rating${r.id}`} value={n} checked={rating === n} onChange={() => setRating(n)} />
            <label htmlFor={`st${r.id}-${n}`}>★</label>
          </span>
        ))}
      </div>
      <textarea className="area" rows={2} maxLength={1000} value={body} onChange={(e) => setBody(e.target.value)} placeholder={t('ratePh')} aria-label={t('ratePh')} />
      <button className="btn sm" type="submit" disabled={busy} style={{ alignSelf: 'flex-start' }}>{t('sendRate')}</button>
    </form>
  );
}

function RequestCard({ r, reload, goDone }) {
  const [pending, setPending] = useState(null);
  const [nego, setNego] = useState(null);   // offer id being countered
  const counter = (o, price) => guarded(async () => {
    await api('/requests/' + r.id + '/offers/' + o.id + '/counter', { method: 'POST', body: { price } });
    toast(t('counterSent'));
    setNego(null);
    reload();
  });
  const col = catColors(r.categoryCode);
  const pillCls = r.status === 'ACCEPTED' ? 'pill-ok' : (r.status === 'OPEN' ? (r.offerCount > 0 ? 'pill-lime' : 'pill-warn') : 'pill-mute');
  const pillTxt = r.status === 'OPEN' && r.offerCount > 0 ? t('offersN', { n: r.offerCount }) : t('st_' + r.status);
  const sure = (act, label) => (pending === act ? t('sure') : label);

  const accept = (offerId) => guarded(async () => {
    await api('/requests/' + r.id + '/offers/' + offerId + '/accept', { method: 'POST' });
    toast(t('accepted'));
    reload();
  });
  const confirmed = (act, path, after) => {
    if (pending !== act) { setPending(act); return; }
    setPending(null);
    guarded(async () => { await api('/requests/' + r.id + '/' + path, { method: 'POST' }); after(); });
  };
  const cancel = () => confirmed('cancel', 'cancel', () => { toast(t('cancelled')); reload(); });
  const complete = () => confirmed('complete', 'complete', () => { goDone(); });

  const o0 = (r.offers || [])[0];
  let body = null;
  if (r.status === 'OPEN') {
    body = (
      <>
        {r.offers && r.offers.length ? (
          <div className="col g10">
            <b style={{ fontSize: 14 }}>{t('offersT')}</b>
            {r.offers.map((o) => (
              <div className="col g8" key={o.id}><div className="offer">
                <Avatar name={L(o, 'artisanName')} id={o.artisanId} />
                <div className="grow col" style={{ gap: 2 }}>
                  <a href={`#/artisan/${o.artisanId}`} className="ell" style={{ fontWeight: 800, fontSize: 14 }}>{L(o, 'artisanName')}</a>
                  <span className="row g4 muted xs">
                    {o.artisanReviewCount > 0 ? <><Icon name="star" size={12} className="star" />{Number(o.artisanRating).toFixed(1)} · </> : null}{etaLabel(o.etaText)}
                  </span>
                  {o.message ? <span className="muted xs">{o.message}</span> : null}
                </div>
                <div className="col" style={{ alignItems: 'flex-end', gap: 5 }}>
                  {o.firstPrice && o.firstPrice !== o.price && o.lastBy === 'ARTISAN' ? <span className="strike xs num">{fmt(o.firstPrice)}</span> : null}
                  <b className="num" style={{ fontSize: 14, whiteSpace: 'nowrap' }}>{o.lastBy === 'CLIENT' ? '…' : money(o.price)}</b>
                  {o.lastBy === 'ARTISAN' ? <button className="btn sm" onClick={() => accept(o.id)}>{t('accept')}</button> : null}
                </div>
              </div>
              {o.lastBy === 'CLIENT' ? <div className="note wait" style={{ marginTop: -4 }}><Icon name="clock" size={18} />{t('waitArtisan', { v: money(o.price) })}</div> : null}
              {o.lastBy === 'ARTISAN' && o.rounds < o.maxRounds ? (
                nego === o.id
                  ? <CounterForm min={r.priceMin} max={r.priceMax} current={o.price} onSend={(p) => counter(o, p)} onCancel={() => setNego(null)} />
                  : <button className="link" style={{ alignSelf: 'flex-start', marginTop: -4 }} onClick={() => setNego(o.id)}>{t('negotiate')} · {t('roundsLeft', { n: o.maxRounds - o.rounds })}</button>
              ) : null}</div>
            ))}
          </div>
        ) : <div className="note wait"><Icon name="clock" size={18} />{t('waitOffers')}</div>}
        <button className="link danger" style={{ alignSelf: 'flex-start' }} onClick={cancel}>{sure('cancel', t('cancelReq'))}</button>
      </>
    );
  } else if (r.status === 'ACCEPTED') {
    body = (
      <>
        <div className="note ok"><Icon name="phone" size={18} /><span>{t('accepted')}{o0 ? ' · ' + L(o0, 'artisanName') + ' · ' + money(o0.price) : ''}</span></div>
        {o0 && o0.artisanPhone ? <a className="btn" href={`tel:${o0.artisanPhone}`}><Icon name="phone" size={18} />{t('callArtisan')}</a> : null}
        <div className="row between g10">
          <button className="btn sm dark" onClick={complete}>{sure('complete', t('markDone'))}</button>
          <button className="link danger" onClick={cancel}>{sure('cancel', t('cancelReq'))}</button>
        </div>
      </>
    );
  } else if (r.status === 'DONE') {
    body = r.reviewed ? <div className="note ok">{t('reviewedMsg')}</div> : <ReviewForm r={r} onDone={reload} />;
  }

  return (
    <div className="card col g12 view-in">
      <div className="row g12">
        <div className="avatar" style={{ width: 46, height: 46, borderRadius: 16, background: col[0], color: col[1] }}><Icon name={r.categoryCode} size={24} /></div>
        <div className="grow col" style={{ gap: 2 }}>
          <b style={{ fontSize: 15, lineHeight: 1.3 }}>{L(r, 'categoryName')}</b>
          <span className="muted xs">{[r.neighborhood || r.city, ago(r.createdAt)].join(' · ')}</span>
        </div>
      </div>
      <div style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.5 }}>{r.description}</div>
      <MediaGallery media={r.media} />
      <RequestPlace r={r} withMap={false} />
      <div className="row between g8">
        <span className="row g6 muted small num"><Icon name="cash" size={16} />{t('budgetLbl', { v: money(r.budget) })}</span>
        <span className={`pill ${pillCls}`} style={{ fontSize: 12.5, padding: '4px 12px', fontWeight: 800 }}>{pillTxt}</span>
      </div>
      {body}
    </div>
  );
}

export default function Requests({ route }) {
  const tab = route.q.get('tab') === 'done' ? 'done' : 'active';
  const st = useAsync(() => api('/requests/mine?tab=' + tab), [tab], { live: true });
  const goDone = () => { if (tab === 'done') st.reload(); else location.hash = '#/requests?tab=done'; };
  return (
    <Screen tab="req" width="narrow">
      <PageHead title={t('reqTitle')} />
      <SegLinks items={[{ href: '#/requests', label: t('tabActive'), on: tab === 'active' }, { href: '#/requests?tab=done', label: t('tabDone'), on: tab === 'done' }]} />
      <Gate st={st} loader={<Skeleton rows={3} />}>
        {(list) => (list.length ? list.map((x) => <RequestCard key={x.id} r={x} reload={st.reload} goDone={goDone} />) : <Empty>{t('emptyReq')}</Empty>)}
      </Gate>
    </Screen>
  );
}
