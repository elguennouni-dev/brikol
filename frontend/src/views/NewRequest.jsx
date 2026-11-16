import { useState } from 'react';
import { api, getCats, useAsync, guarded, toast, t, L, fmt, back, useSession } from '../session';
import { LocationSheet, MapPreview } from '../places';
import { centerOf } from '../geo';
import { Icon, CashBanner, Screen, BackButton, Loader } from '../ui';

// The draft survives navigation (the user may leave the screen and come back).
const blank = () => ({ cat: 'paint', desc: '', hood: '', when: 'WEEK', budget: 1500, place: null });
let draft = blank();

export default function NewRequest() {
  const [f, setF] = useState(draft);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);
  const { user } = useSession();
  const cats = useAsync(getCats, []);
  const set = (patch) => setF((old) => { draft = { ...old, ...patch }; return draft; });

  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    guarded(async () => {
      if (!f.desc || f.desc.trim().length < 5) throw { code: 'VALIDATION' };
      if (!f.place) { setPicking(true); throw { code: 'PLACE_REQUIRED' }; }
      const p = f.place;
      await api('/requests', { method: 'POST', body: { categoryCode: f.cat, description: f.desc.trim(), neighborhood: f.hood || p.hood || null, city: p.city || null, lat: p.lat, lng: p.lng, address: p.address || null, whenPref: f.when, budget: f.budget } });
      draft = blank();
      toast(t('published'));
      location.hash = '#/requests';
    }).then(() => setBusy(false));
  };

  const bar = <div className="bar"><button className="btn block" type="submit" form="newform" disabled={busy}>{t('publish')}</button></div>;

  return (
    <Screen tab="plus" nav={false} bar={bar}>
      <div className="row g12"><BackButton onClick={back} /><h1 className="h1" style={{ fontSize: 22 }}>{t('newTitle')}</h1></div>
      <form id="newform" onSubmit={submit} className="col" style={{ gap: 24 }} noValidate>
        <div>
          <span className="lbl">{t('qService')}</span>
          {cats.data ? (
            <div className="chips wrap">
              {cats.data.map((c) => <button key={c.code} type="button" className={`chip ${f.cat === c.code ? 'on' : ''}`} onClick={() => set({ cat: c.code })}>{L(c, 'name')}</button>)}
            </div>
          ) : <Loader label={false} size={40} />}
        </div>
        <div>
          <label className="lbl" htmlFor="desc">{t('qDesc')}</label>
          <textarea id="desc" className="area" rows={4} maxLength={2000} value={f.desc} onChange={(e) => set({ desc: e.target.value })} placeholder={t('descPh')} />
        </div>
        <div>
          <span className="lbl">{t('qWhere')}</span>
          {f.place ? (
            <div className="col g8">
              <button type="button" className="placecard" onClick={() => setPicking(true)}>
                <span className="pdot"><Icon name="pin" size={20} /></span>
                <span className="col grow" style={{ minWidth: 0, gap: 2 }}><b className="ell">{f.place.address}</b>{f.place.city ? <span className="muted xs">{f.place.city}</span> : null}</span>
                <span className="go">{t('mapChange')}</span>
              </button>
              <MapPreview lat={f.place.lat} lng={f.place.lng} height={130} />
            </div>
          ) : (
            <button type="button" className="placecard" onClick={() => setPicking(true)}>
              <span className="pdot"><Icon name="target" size={20} /></span>
              <span className="col grow" style={{ minWidth: 0, gap: 2 }}><b>{t('mapPick')}</b><span className="muted xs">{t('mapPickHint')}</span></span>
            </button>
          )}
          <input className="input" style={{ marginTop: 8 }} type="text" maxLength={80} value={f.hood} onChange={(e) => set({ hood: e.target.value })} placeholder={t('hood')} aria-label={t('hood')} />
        </div>
        <div>
          <span className="lbl">{t('qWhen')}</span>
          <div className="row g8">
            {['NOW', 'WEEK', 'FLEXIBLE'].map((w) => (
              <button key={w} type="button" className={`chip ${f.when === w ? 'on' : ''}`} style={{ flex: 1, height: 46, padding: '0 6px', borderRadius: 16 }} onClick={() => set({ when: w })}>{t('when' + w)}</button>
            ))}
          </div>
        </div>
        <div>
          <span className="lbl">{t('qBudget')}</span>
          <div className="budget">
            <div className="row between" style={{ width: '100%' }}>
              <button type="button" className="step" onClick={() => set({ budget: Math.max(0, f.budget - 100) })} aria-label="-100"><Icon name="minus" size={20} /></button>
              <div className="row g6" style={{ alignItems: 'baseline' }}><span className="amt num">{fmt(f.budget)}</span><b>{t('dh')}</b></div>
              <button type="button" className="step plus" onClick={() => set({ budget: Math.min(1000000, f.budget + 100) })} aria-label="+100"><Icon name="plus" size={20} /></button>
            </div>
            <span className="xs" style={{ color: '#C3D4CB', textAlign: 'center' }}>{t('budgetHint')}</span>
          </div>
        </div>
        <CashBanner />
      </form>
      {picking ? (
        <LocationSheet
          start={f.place || centerOf(user && user.city)}
          autoLocate={!f.place}
          onClose={() => setPicking(false)}
          onPick={(p) => { set({ place: p, hood: f.hood || p.hood || '' }); setPicking(false); }}
        />
      ) : null}
    </Screen>
  );
}
