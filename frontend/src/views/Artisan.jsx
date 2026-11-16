import { api, useAsync, t, L, fmt, initials, ago, back } from '../session';
import { IC, Icon, Verified, Screen, Gate, BackButton, PAL } from '../ui';

export default function Artisan({ route }) {
  const st = useAsync(() => api('/artisans/' + encodeURIComponent(route.id)), [route.id]);
  const d = st.data;
  const a = d && d.summary;
  const col = a ? PAL[Math.abs(a.id) % PAL.length] : PAL[0];
  const tel = d ? String(d.phone || '') : '';
  const wa = d ? String(d.whatsapp || d.phone || '').replace(/\D/g, '') : '';

  const bar = d ? (
    <div className="bar">
      <div className="row g10">
        <a className="btn" href={`tel:${tel}`}><Icon name="phone" size={20} />{t('call')}</a>
        {wa ? <a className="btn wa" href={`https://wa.me/${wa}`} target="_blank" rel="noopener noreferrer"><Icon name="chat" size={20} />{t('whatsapp')}</a> : null}
      </div>
      <div className="row g6 muted xs" style={{ justifyContent: 'center' }}><Icon name="cash" size={15} />{t('cashShort')}</div>
    </div>
  ) : null;

  return (
    <Screen tab="explore" nav={false} flush bar={bar}>
      <Gate st={st} loader={
        <div className="col bleed">
          <div className="cover" style={{ background: PAL[0][0] }}><BackButton onClick={back} /></div>
          <div className="sheet"><div className="skel" style={{ height: 90 }} /><div className="skel" /><div className="skel" /></div>
        </div>
      }>
        {() => (
          <div className="col bleed">
            <div className="cover" style={{ background: col[0] }}>
              <svg className="deco" width="200" height="200" viewBox="0 0 24 24" aria-hidden="true" style={{ stroke: col[1] }}><path d={IC.tile} /></svg>
              <BackButton onClick={back} />
            </div>
            <div className="sheet">
              <div className="row between" style={{ alignItems: 'flex-end' }}>
                <div className="bigavatar" style={{ background: col[0], color: col[1] }}>{initials(L(a, 'name'))}</div>
                <span className={`pill ${a.availableToday ? 'pill-ok' : 'pill-mute'}`} style={{ marginBottom: 6, padding: '5px 12px', fontSize: 12.5 }}>{t(a.availableToday ? 'availToday' : 'availTomorrow')}</span>
              </div>
              <div className="col g4">
                <div className="row g8" style={{ fontSize: 23, fontWeight: 800 }}>{L(a, 'name')}{a.verified ? <Verified size={22} /> : null}</div>
                <div className="row g6 muted" style={{ fontSize: 14 }}><span>{L(a, 'trade')}</span><span>·</span><Icon name="pin" size={14} /><span>{L(a, 'neighborhood')}</span></div>
              </div>
              <div className="stats">
                <div className="stat"><b className="num">{a.reviewCount > 0 ? <><Icon name="star" size={16} className="star" />{Number(a.rating).toFixed(1)}</> : t('newBadge')}</b><span className="muted xs" style={{ textAlign: 'center' }}>{t('rating')}</span></div>
                <div className="stat"><b className="num">{a.jobsDone}</b><span className="muted xs" style={{ textAlign: 'center' }}>{t('jobs')}</span></div>
                <div className="stat"><b className="num">{a.yearsExperience}</b><span className="muted xs" style={{ textAlign: 'center' }}>{t('yearsExp')}</span></div>
              </div>
              {L(d, 'bio') ? <div className="col g8"><h2 className="h2" style={{ fontSize: 17 }}>{t('about')}</h2><span style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.6 }}>{L(d, 'bio')}</span></div> : null}
              {(d.services || []).length ? (
                <div className="col g10">
                  <h2 className="h2" style={{ fontSize: 17 }}>{t('servicesT')}</h2>
                  <div className="card" style={{ padding: '0 16px', borderRadius: 20 }}>
                    {d.services.map((s) => (
                      <div key={s.id} className="svc"><span style={{ fontWeight: 600, fontSize: 14 }}>{L(s, 'label')}</span>
                        <span className="num" style={{ fontWeight: 800, whiteSpace: 'nowrap' }}>{t('from')} {fmt(s.price)} {t('dh')}{L(s, 'unit')}</span></div>
                    ))}
                  </div>
                </div>
              ) : null}
              <div className="col g10">
                <h2 className="h2" style={{ fontSize: 17 }}>{t('reviewsT')}</h2>
                {(d.reviews || []).length ? d.reviews.map((v) => (
                  <div key={v.id} className="card col g6" style={{ borderRadius: 20, padding: '14px 16px' }}>
                    <div className="row between"><b style={{ fontSize: 14 }}>{v.clientName}</b><span className="row g4 small" style={{ fontWeight: 800 }}><Icon name="star" size={14} className="star" />{v.rating}.0</span></div>
                    {v.body ? <span style={{ fontSize: 13.5, color: 'var(--ink-2)', lineHeight: 1.55 }}>{v.body}</span> : null}
                    <span className="muted xs">{ago(v.createdAt)}</span>
                  </div>
                )) : <span className="muted small">{t('noReviews')}</span>}
              </div>
            </div>
          </div>
        )}
      </Gate>
    </Screen>
  );
}
