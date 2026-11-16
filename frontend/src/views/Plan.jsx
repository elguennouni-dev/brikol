import { api, useAsync, t, fmt, getSession } from '../session';
import { Screen, PageHead, Gate, Skeleton, Icon } from '../ui';

const day = (iso) => new Date(iso).toLocaleDateString(getSession().lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });

/** Artisan: subscription status, featured placement, how to pay (outside the app), payment history. */
export default function Plan() {
  const st = useAsync(() => api('/artisan/me/plan'), []);
  return (
    <Screen tab="me" width="narrow">
      <PageHead title={t('planTitle')} />
      <Gate st={st} loader={<Skeleton rows={3} />}>{(p) => (
        <>
          {!p.monetization ? <div className="note ok"><Icon name="checkMark" size={18} />{t('planFree')}</div> : (
            <div className={`card col g8 ${p.active ? '' : 'warn-card'}`}>
              <b style={{ fontSize: 17 }}>{p.active ? t('planActive') : t('planExpired')}</b>
              {p.subscriptionUntil ? <span className="muted small">{t('planUntil', { d: day(p.subscriptionUntil) })}{p.active ? ' · ' + t('planDays', { n: p.daysLeft }) : ''}</span> : null}
              <span className="small">{t('planPrice', { v: fmt(p.subscriptionPrice) })}</span>
            </div>
          )}
          {p.monetization ? (
            <div className="card col g8">
              <b className="row g6"><Icon name="crown" size={16} />{t('featured')}</b>
              <span className="muted small">{p.featured ? t('featuredUntil', { d: day(p.featuredUntil) }) : t('featuredPitch', { v: fmt(p.featuredPrice) })}</span>
            </div>
          ) : null}
          {p.monetization && p.instructions ? (
            <div className="card col g8"><b>{t('planHowPay')}</b><p className="small" style={{ margin: 0, whiteSpace: 'pre-wrap' }} dir="auto">{p.instructions}</p></div>
          ) : null}
          {p.payments.length ? (
            <div className="card col g6">
              <b>{t('planPayments')}</b>
              {p.payments.map((x) => <div key={x.id} className="row between g8 line small"><span>{t('pk_' + x.kind)} · {x.months} {t('months')}</span><span className="num">{fmt(x.amount)} {t('dh')} · {day(x.createdAt)}</span></div>)}
            </div>
          ) : null}
        </>
      )}</Gate>
    </Screen>
  );
}
