import { getCats, useAsync, useSession, t, L, isArtisan, artisanReady } from '../session';
import { IC, Icon, CashBanner, Screen, Gate, Skeleton, LangButton, Bell, catColors } from '../ui';
import { setLang } from '../session';

export default function Home() {
  const { user, lang } = useSession();
  const st = useAsync(getCats, []);
  const art = isArtisan();
  const first = user ? String(user.fullName || '').split(' ')[0] : '';
  const cta = art ? '#/jobs' : '#/new';
  const catHref = (code) => (art ? '#/jobs' : `#/new?cat=${encodeURIComponent(code)}`);

  return (
    <Screen tab="home">
      <div className="row between g12 m-only">
        <div className="row g10" style={{ minWidth: 0 }}>
          <img className="brand-mark" src="/app/brand/mark.png" alt="Brikol" />
          <div className="col" style={{ minWidth: 0 }}>
            <span className="muted xs">{user ? t('hello', { name: first }) : t('helloGuest')}</span>
            <span className="row g4" style={{ fontWeight: 800 }}><Icon name="pin" size={15} />{t('city')}</span>
          </div>
        </div>
        <div className="row g8"><Bell /><LangButton onClick={() => setLang(lang === 'ar' ? 'fr' : 'ar')} /></div>
      </div>

      {isArtisan() && !artisanReady() ? (
        <a className="note wait" href="#/onboarding" style={{ textDecoration: 'none' }}><Icon name="doc" size={18} /><span className="grow">{t('finishFile')}</span><b>{t('openFile')} ›</b></a>
      ) : null}

      <div className="hero">
        <svg className="deco" width="170" height="170" viewBox="0 0 24 24" aria-hidden="true" style={{ insetInlineEnd: -38, top: -40 }}><path d={IC.tile} /></svg>
        <svg className="deco" width="90" height="90" viewBox="0 0 24 24" aria-hidden="true" style={{ insetInlineEnd: 60, bottom: -34, opacity: 0.2 }}><path d={IC.tile} /></svg>
        <div className="t">{t(art ? 'artHeroTitle' : 'heroTitle')}</div>
        <div className="s">{t(art ? 'artHeroSub' : 'heroSub')}</div>
        <a className="btn" href={cta}><Icon name={art ? 'clip' : 'plus'} size={18} />{t(art ? 'artHeroBtn' : 'heroBtn')}</a>
      </div>

      <Gate st={st} loader={<Skeleton rows={2} />}>
        {(cats) => (
          <div className="col g12">
            <h2 className="h2">{t('services')}</h2>
            <div className="cats">
              {cats.map((c, i) => {
                const col = catColors(c.code);
                return (
                  <a key={c.code} className="cat stagger" style={{ animationDelay: `${i * 40}ms` }} href={catHref(c.code)}>
                    <span className="tile" style={{ background: col[0], color: col[1] }}><Icon name={c.code} size={30} /></span>
                    <span className="l">{L(c, 'name')}</span>
                  </a>
                );
              })}
              <a className="cat" href={cta}>
                <span className="tile" style={{ background: '#E6EAE2', color: '#0E2B22' }}><Icon name="more" size={30} /></span>
                <span className="l">{t('more')}</span>
              </a>
            </div>
          </div>
        )}
      </Gate>

      {art ? (
        <div className="tipcard"><Icon name="target" size={22} /><div className="col" style={{ gap: 2 }}><b>{t('artTipTitle')}</b><span className="small">{t('artTipText')}</span></div></div>
      ) : (
        <div className="col g12">
          <h2 className="h2">{t('whyTitle')}</h2>
          <div className="feats">
            {[['camera', 'f1t', 'f1d', 0], ['target', 'f2t', 'f2d', 1], ['shield', 'f3t', 'f3d', 2], ['cash', 'f4t', 'f4d', 3]].map(([ic, ti, d, i]) => (
              <div key={ti} className={`feat stagger f${i}`} style={{ animationDelay: `${i * 60}ms` }}>
                <span className="fi"><Icon name={ic} size={22} /></span>
                <b>{t(ti)}</b>
                <span className="muted small">{t(d)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card col g16">
        <h2 className="h2">{t('howTitle')}</h2>
        <div className="steps col g16">
          {[['s1t', 's1d'], ['s2t', 's2d'], ['s3t', 's3d']].map((s, i) => (
            <div key={s[0]} className="row g12" style={{ alignItems: 'flex-start' }}>
              <div className="n">{i + 1}</div>
              <div className="col" style={{ gap: 2 }}><b style={{ fontSize: 15 }}>{t(s[0])}</b><span className="muted small">{t(s[1])}</span></div>
            </div>
          ))}
        </div>
      </div>
      {art || user ? null : (
        <a className="promo" href="#/login?mode=register&role=ARTISAN">
          <svg className="deco" width="120" height="120" viewBox="0 0 24 24" aria-hidden="true"><path d={IC.tile} /></svg>
          <span className="col" style={{ gap: 4 }}><b>{t('proTitle')}</b><span className="small">{t('proText')}</span></span>
          <span className="btn sm">{t('proBtn')} ›</span>
        </a>
      )}
      <CashBanner />
    </Screen>
  );
}
