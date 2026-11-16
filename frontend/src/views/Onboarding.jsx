import { useEffect, useState } from 'react';
import { api, getCats, useAsync, guarded, toast, t, L, refreshMe, useSession } from '../session';
import { Screen, PageHead, Icon, Loader, Empty } from '../ui';
import { ImageSlot, PrivateImg, Lightbox } from '../media';
import { LocationSheet, MapPreview } from '../places';
import { centerOf } from '../geo';

const STATUS_NOTE = { DRAFT: 'wait', PENDING: 'wait', APPROVED: 'ok', REJECTED: 'wait' };

/** The artisan's activation file: identity, profile, work area and portfolio. An admin approves it. */
export default function Onboarding() {
  const { user } = useSession();
  const st = useAsync(() => api('/artisan/me/onboarding'), []);
  const cats = useAsync(getCats, []);
  const [f, setF] = useState(null);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);
  const [big, setBig] = useState(null);

  useEffect(() => {
    if (st.data) {
      const d = st.data;
      setF({
        fullName: d.fullName || '', cin: d.cin || '', yearsExperience: String(d.yearsExperience || ''), bio: d.bio || '',
        categoryCode: d.categoryCode, whatsapp: d.whatsapp || '', radiusKm: d.radiusKm || 15,
        place: d.lat != null ? { lat: d.lat, lng: d.lng, address: d.address || '', city: d.city } : null, city: d.city,
        avatar: d.avatar, idFront: d.idFront, idBack: d.idBack, portfolio: d.portfolio || []
      });
    }
  }, [st.data]);

  if (st.error) return <Screen tab="me" width="narrow"><Empty onRetry={st.reload}>{t('err_GENERIC')}</Empty></Screen>;
  if (!f || !st.data) return <Screen tab="me" width="narrow"><Loader /></Screen>;

  const d = st.data;
  const status = d.status;
  const pending = status === 'PENDING';
  const locked = status === 'APPROVED';          // identity and trade
  const set = (patch) => setF((x) => ({ ...x, ...patch }));

  const body = () => ({
    fullName: f.fullName, cin: f.cin, yearsExperience: f.yearsExperience === '' ? null : Math.min(70, parseInt(f.yearsExperience, 10) || 0),
    bio: f.bio, categoryCode: f.categoryCode, whatsapp: f.whatsapp || null, radiusKm: f.radiusKm,
    city: f.place && f.place.city ? f.place.city : null, address: f.place ? f.place.address : null,
    lat: f.place ? f.place.lat : null, lng: f.place ? f.place.lng : null,
    avatarId: f.avatar ? f.avatar.id : null,
    idFrontId: !locked && f.idFront ? f.idFront.id : null, idBackId: !locked && f.idBack ? f.idBack.id : null,
    portfolioIds: f.portfolio.map((m) => m.id)
  });
  const save = (andSubmit) => {
    setBusy(true);
    guarded(async () => {
      await api('/artisan/me/onboarding', { method: 'PUT', body: body() });
      if (andSubmit) {
        await api('/artisan/me/onboarding/submit', { method: 'POST' });
        toast(t('obSent'));
        await refreshMe();
      } else toast(t('obSaved'));
      st.reload();
    }).then(() => setBusy(false));
  };

  const missing = d.missing || [];
  const allKeys = ['fullName', 'cin', 'idFront', 'idBack', 'avatar', 'bio', 'place', 'portfolio'];
  const noteText = status === 'REJECTED' ? t('ob_REJECTED', { r: d.rejectionReason || '' }) : t('ob_' + status);

  return (
    <Screen tab="me" width="narrow">
      <PageHead title={t('obTitle')} />
      <div className={`note ${STATUS_NOTE[status]}`}><Icon name={status === 'APPROVED' ? 'checkMark' : status === 'PENDING' ? 'clock' : 'doc'} size={18} /><span>{noteText}</span></div>
      {status === 'APPROVED' && user && user.artisanId ? <a className="btn sm ghost" style={{ alignSelf: 'flex-start' }} href={`#/artisan/${user.artisanId}`}>{t('seeAll')} · {t('obProfile')}</a> : null}
      {!locked ? <span className="muted small">{t('obIntro')}</span> : <span className="muted xs">{t('obLocked')}</span>}
      {!pending && !locked ? (
        <div className="checklist">{allKeys.map((k) => <span key={k} className={missing.includes(k) ? '' : 'ok'}>{missing.includes(k) ? '○' : '✓'} {t('m_' + k)}</span>)}</div>
      ) : null}

      <fieldset disabled={pending || busy} className="col g16" style={{ border: 'none', padding: 0, margin: 0 }}>
        {/* 1. identity */}
        <div className="card col g12">
          <div className="obstep"><span className="n">1</span><b>{t('obIdentity')}</b></div>
          <div><label className="lbl" htmlFor="obn">{t('fullName')}</label><input id="obn" className="input" maxLength={120} disabled={locked} value={f.fullName} onChange={(e) => set({ fullName: e.target.value })} /></div>
          <div><label className="lbl" htmlFor="obc">{t('obCin')}</label><input id="obc" className="input" maxLength={20} dir="ltr" style={{ textAlign: 'start', textTransform: 'uppercase' }} disabled={locked} placeholder={t('obCinPh')} value={f.cin} onChange={(e) => set({ cin: e.target.value })} /></div>
          {!locked ? (
            <>
              <div className="grid2c">
                <div className="col g6"><span className="small" style={{ fontWeight: 700 }}>{t('obIdFront')}</span><ImageSlot label={t('obIdFront')} purpose="ID_FRONT" value={f.idFront} privateSrc onUploaded={(m) => set({ idFront: m })} /></div>
                <div className="col g6"><span className="small" style={{ fontWeight: 700 }}>{t('obIdBack')}</span><ImageSlot label={t('obIdBack')} purpose="ID_BACK" value={f.idBack} privateSrc onUploaded={(m) => set({ idBack: m })} /></div>
              </div>
              <span className="muted xs"><Icon name="shield" size={13} /> {t('obIdNote')}</span>
            </>
          ) : null}
        </div>

        {/* 2. profile */}
        <div className="card col g12">
          <div className="obstep"><span className="n">2</span><b>{t('obProfile')}</b></div>
          <div className="row g12">
            <ImageSlot label={t('obAvatar')} purpose="AVATAR" value={f.avatar} round onUploaded={(m) => set({ avatar: m })} />
            <div className="col g8 grow" style={{ minWidth: 0 }}>
              <label className="lbl" htmlFor="obt" style={{ marginBottom: 0 }}>{t('trade')}</label>
              <select id="obt" className="select" disabled={locked} value={f.categoryCode} onChange={(e) => set({ categoryCode: e.target.value })}>
                {(cats.data || []).map((c) => <option key={c.code} value={c.code}>{L(c, 'name')}</option>)}
              </select>
            </div>
          </div>
          <div className="grid2c">
            <div><label className="lbl" htmlFor="oby">{t('obYears')}</label><input id="oby" className="input" type="text" inputMode="numeric" disabled={locked} value={f.yearsExperience} onChange={(e) => set({ yearsExperience: e.target.value.replace(/[^\d]/g, '').slice(0, 2) })} /></div>
            <div><label className="lbl" htmlFor="obw">{t('obWhatsapp')}</label><input id="obw" className="input" type="tel" inputMode="tel" dir="ltr" style={{ textAlign: 'start' }} value={f.whatsapp} onChange={(e) => set({ whatsapp: e.target.value })} /></div>
          </div>
          <div><label className="lbl" htmlFor="obb">{t('obBio')}</label><textarea id="obb" className="area" rows={4} maxLength={1000} value={f.bio} onChange={(e) => set({ bio: e.target.value })} />
            <span className="muted xs">{f.bio.trim().length} / 30+</span></div>
        </div>

        {/* 3. work area */}
        <div className="card col g12">
          <div className="obstep"><span className="n">3</span><b>{t('obZone')}</b></div>
          <button type="button" className="placecard" onClick={() => setPicking(true)}>
            <span className="pdot"><Icon name={f.place ? 'pin' : 'target'} size={20} /></span>
            <span className="col grow" style={{ minWidth: 0, gap: 2 }}><b className="ell">{f.place ? (f.place.address || f.place.city) : t('obPlace')}</b>{f.place && f.place.city ? <span className="muted xs">{f.place.city}</span> : <span className="muted xs">{t('mapPickHint')}</span>}</span>
            {f.place ? <span className="go">{t('mapChange')}</span> : null}
          </button>
          <label className="col g6"><b className="small">{t('obRadius', { n: f.radiusKm })}</b>
            <input type="range" min="1" max="100" step="1" value={f.radiusKm} onChange={(e) => set({ radiusKm: parseInt(e.target.value, 10) })} /></label>
          {f.place ? <MapPreview lat={f.place.lat} lng={f.place.lng} radiusKm={f.radiusKm} height={180} /> : null}
        </div>

        {/* 4. portfolio */}
        <div className="card col g12">
          <div className="obstep"><span className="n">4</span><b>{t('obPortfolio')}</b></div>
          <div className="mgrid">
            {f.portfolio.map((m) => (
              <div key={m.id} className="mthumb">
                <img src={m.url} alt="" onClick={() => setBig(m.url)} />
                <button type="button" className="mdel" onClick={() => set({ portfolio: f.portfolio.filter((x) => x.id !== m.id) })} aria-label={t('a_delete')}>×</button>
              </div>
            ))}
            {f.portfolio.length < 12 ? <ImageSlot label="+" purpose="PORTFOLIO" value={null} onUploaded={(m) => setF((x) => ({ ...x, portfolio: [...x.portfolio, m] }))} /> : null}
          </div>
        </div>
      </fieldset>

      {!pending ? (
        <div className="col g8">
          <button className="btn ghost block" disabled={busy} onClick={() => save(false)}>{t('obSave')}</button>
          {!locked ? <button className="btn block" disabled={busy} onClick={() => save(true)}>{t('obSubmit')}</button> : null}
        </div>
      ) : null}

      {picking ? (
        <LocationSheet start={f.place || centerOf(f.city)} autoLocate={!f.place} onClose={() => setPicking(false)}
          onPick={(p) => { set({ place: p }); setPicking(false); }} />
      ) : null}
      {big ? <Lightbox src={big} onClose={() => setBig(null)} /> : null}
    </Screen>
  );
}
