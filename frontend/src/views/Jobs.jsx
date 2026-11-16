import { useState } from 'react';
import { RequestPlace } from '../places';
import { MediaGallery } from '../media';
import { PriceField, CounterForm, inRange, toInt } from '../price';
import { fmt } from '../session';
import { api, useAsync, guarded, toast, t, L, money, ago } from '../session';
import { Icon, SegLinks, Screen, Gate, Empty, Skeleton, PageHead, catColors } from '../ui';

function OfferForm({ job, reload }) {
  const [price, setPrice] = useState(String(job.budget || ''));
  const [eta, setEta] = useState('TODAY');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      const p = toInt(price);
      if (!inRange(p, job.priceMin, job.priceMax)) throw { code: 'PRICE_OUT_OF_RANGE' };
      await api('/requests/' + job.id + '/offers', { method: 'POST', body: { price: p, etaText: eta, message: message || null } });
      toast(t('offerSent'));
      reload();
    }).then(() => setBusy(false));
  };
  return (
    <form onSubmit={submit} className="col g10" noValidate>
      <PriceField value={price} onChange={setPrice} min={job.priceMin} max={job.priceMax} />
      <div className="row g8">
        <select className="select" style={{ flex: 1, minWidth: 0 }} value={eta} onChange={(e) => setEta(e.target.value)} aria-label={t('etaWhen')}>
          {['TODAY', 'TOMORROW', 'WEEK'].map((e) => <option key={e} value={e}>{t('eta_' + e)}</option>)}
        </select>
      </div>
      <input className="input" type="text" maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)} placeholder={t('msgPh')} aria-label={t('msgPh')} />
      <button className="btn sm" type="submit" disabled={busy} style={{ alignSelf: 'flex-start' }}>{t('sendOffer')}</button>
    </form>
  );
}

// Negotiation seen by the artisan: answer the client's counter-offer (agree or propose another price).
function Negotiation({ x, mine, reload }) {
  const [counter, setCounter] = useState(false);
  const left = mine.maxRounds - mine.rounds;
  const agree = () => guarded(async () => { await api('/artisan/offers/' + mine.id + '/agree', { method: 'POST' }); toast(t('agreed')); reload(); });
  const send = (price) => guarded(async () => { await api('/artisan/offers/' + mine.id + '/counter', { method: 'POST', body: { price } }); toast(t('counterSent')); setCounter(false); reload(); });
  if (mine.status !== 'PENDING' || x.status !== 'OPEN') return null;
  if (mine.lastBy !== 'CLIENT') return <div className="note wait"><Icon name="clock" size={18} />{t('waitClient')}</div>;
  return (
    <div className="nego">
      <b>{t('clientProposes', { v: money(mine.price) })}</b>
      {counter ? <CounterForm min={x.priceMin} max={x.priceMax} current={mine.price} onSend={send} onCancel={() => setCounter(false)} /> : (
        <div className="row g8 wrap">
          <button className="btn sm" onClick={agree}>{t('acceptPrice', { v: money(mine.price) })}</button>
          {left > 0 ? <button className="btn sm ghost" onClick={() => setCounter(true)}>{t('otherPrice')}</button> : null}
        </div>
      )}
      {left > 0 ? <span className="muted xs">{t('roundsLeft', { n: left })}</span> : null}
    </div>
  );
}

function JobCard({ x, reload }) {
  const col = catColors(x.categoryCode);
  const mine = x.myOffer;
  let extra = null;
  if (mine) {
    extra = (
      <>
        <div className="row between g8">
          <span className="num small" style={{ fontWeight: 700 }}>
            {mine.firstPrice && mine.firstPrice !== mine.price ? <><span className="strike">{fmt(mine.firstPrice)}</span> </> : null}
            {t('myOffer', { v: money(mine.price) })}
          </span>
          <span className={`pill ${mine.status === 'ACCEPTED' ? 'pill-ok' : (mine.status === 'REJECTED' ? 'pill-mute' : 'pill-warn')}`}>{t('st_' + mine.status)}</span>
        </div>
        <Negotiation x={x} mine={mine} reload={reload} />
        {x.clientPhone ? <a className="btn" href={`tel:${x.clientPhone}`}><Icon name="phone" size={18} />{t('clientCall')} · {x.clientName}</a> : null}
      </>
    );
  } else if (x.status === 'OPEN') {
    extra = <OfferForm job={x} reload={reload} />;
  }
  return (
    <div className="card col g12 view-in">
      <div className="row g12">
        <div className="avatar" style={{ width: 46, height: 46, borderRadius: 16, background: col[0], color: col[1] }}><Icon name={x.categoryCode} size={24} /></div>
        <div className="grow col" style={{ gap: 2 }}><b>{L(x, 'categoryName')}</b><span className="muted xs">{[x.neighborhood || x.city, ago(x.createdAt), t('when' + x.whenPref)].join(' · ')}</span></div>
        {x.distanceKm != null ? <span className="distbadge"><Icon name="pin" size={13} />{t('kmAway', { n: String(x.distanceKm).replace('.', ',') })}</span> : null}
      </div>
      <div style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.5 }}>{x.description}</div>
      <MediaGallery media={x.media} />
      <RequestPlace r={x} directions />
      <span className="row g6 muted small num"><Icon name="cash" size={16} />{t('budgetLbl', { v: money(x.budget) })}</span>
      {extra}
    </div>
  );
}

export default function Jobs({ route }) {
  const tab = route.q.get('tab') === 'mine' ? 'mine' : 'open';
  const st = useAsync(() => api(tab === 'mine' ? '/artisan/jobs' : '/artisan/requests'), [tab], { live: true });
  return (
    <Screen tab={tab === 'mine' ? 'req' : 'plus'} width="narrow">
      <PageHead title={t('jobsTitle')} />
      <SegLinks items={[{ href: '#/jobs', label: t('tabOpen'), on: tab === 'open' }, { href: '#/jobs?tab=mine', label: t('tabMine'), on: tab === 'mine' }]} />
      <Gate st={st} loader={<Skeleton rows={3} />}>
        {(list) => (list.length ? list.map((x) => <JobCard key={x.id} x={x} reload={st.reload} />) : <Empty>{t('emptyJobs')}</Empty>)}
      </Gate>
    </Screen>
  );
}
