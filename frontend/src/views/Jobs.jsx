import { useState } from 'react';
import { RequestPlace } from '../places';
import { api, useAsync, guarded, toast, t, L, money, ago } from '../session';
import { Icon, SegLinks, Screen, Gate, Empty, Skeleton, PageHead, catColors } from '../ui';

function OfferForm({ job, reload }) {
  const [price, setPrice] = useState(job.budget || '');
  const [eta, setEta] = useState('TODAY');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      const p = parseInt(price, 10);
      if (!(p > 0)) throw { code: 'VALIDATION' };
      await api('/requests/' + job.id + '/offers', { method: 'POST', body: { price: p, etaText: eta, message: message || null } });
      toast(t('offerSent'));
      reload();
    }).then(() => setBusy(false));
  };
  return (
    <form onSubmit={submit} className="col g10" noValidate>
      <div className="row g8">
        <input className="input" style={{ flex: 1, minWidth: 0 }} type="number" inputMode="numeric" min="1" value={price} onChange={(e) => setPrice(e.target.value)} aria-label={t('yourPrice')} placeholder={t('yourPrice')} />
        <select className="select" style={{ flex: 1, minWidth: 0 }} value={eta} onChange={(e) => setEta(e.target.value)} aria-label={t('etaWhen')}>
          {['TODAY', 'TOMORROW', 'WEEK'].map((e) => <option key={e} value={e}>{t('eta_' + e)}</option>)}
        </select>
      </div>
      <input className="input" type="text" maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)} placeholder={t('msgPh')} aria-label={t('msgPh')} />
      <button className="btn sm" type="submit" disabled={busy} style={{ alignSelf: 'flex-start' }}>{t('sendOffer')}</button>
    </form>
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
          <span className="num small" style={{ fontWeight: 700 }}>{t('myOffer', { v: money(mine.price) })}</span>
          <span className={`pill ${mine.status === 'ACCEPTED' ? 'pill-ok' : (mine.status === 'REJECTED' ? 'pill-mute' : 'pill-warn')}`}>{t('st_' + mine.status)}</span>
        </div>
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
      </div>
      <div style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.5 }}>{x.description}</div>
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
