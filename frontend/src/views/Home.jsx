import { api, getCats, useAsync, useSession, t, L, isArtisan } from '../session';
import { IC, Icon, ArtisanCard, CashBanner, Screen, Gate, Skeleton, LangButton, Bell, catColors } from '../ui';
import { setLang } from '../session';

export default function Home() {
  const { user, lang } = useSession();
  const st = useAsync(() => Promise.all([getCats(), api('/artisans')]), []);
  const first = user ? String(user.fullName || '').split(' ')[0] : '';
  const cta = isArtisan() ? '#/jobs' : '#/new';

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

      <a className="search" href="#/explore">
        <Icon name="search" size={20} /><span className="grow ell" style={{ fontSize: 14.5 }}>{t('search')}</span>
        <span className="go"><Icon name="sliders" size={18} /></span>
      </a>

      <div className="hero">
        <svg className="deco" width="170" height="170" viewBox="0 0 24 24" aria-hidden="true" style={{ insetInlineEnd: -38, top: -40 }}><path d={IC.tile} /></svg>
        <svg className="deco" width="90" height="90" viewBox="0 0 24 24" aria-hidden="true" style={{ insetInlineEnd: 60, bottom: -34, opacity: 0.2 }}><path d={IC.tile} /></svg>
        <div className="t">{t('heroTitle')}</div>
        <div className="s">{t('heroSub')}</div>
        <a className="btn" href={cta}><Icon name="plus" size={18} />{t('heroBtn')}</a>
      </div>

      <Gate st={st} loader={<Skeleton rows={4} />}>
        {([cats, arts]) => (
          <>
            <div className="col g12">
              <div className="row between"><h2 className="h2">{t('services')}</h2><a className="link" href="#/explore">{t('seeAll')}</a></div>
              <div className="cats">
                {cats.map((c, i) => {
                  const col = catColors(c.code);
                  return (
                    <a key={c.code} className="cat stagger" style={{ animationDelay: `${i * 40}ms` }} href={`#/explore?cat=${encodeURIComponent(c.code)}`}>
                      <span className="tile" style={{ background: col[0], color: col[1] }}><Icon name={c.code} size={30} /></span>
                      <span className="l">{L(c, 'name')}</span>
                    </a>
                  );
                })}
                <a className="cat" href="#/explore">
                  <span className="tile" style={{ background: '#E6EAE2', color: '#0E2B22' }}><Icon name="more" size={30} /></span>
                  <span className="l">{t('more')}</span>
                </a>
              </div>
            </div>
            <div className="col g12"><h2 className="h2">{t('nearby')}</h2><div className="agrid">{arts.slice(0, 6).map((a) => <ArtisanCard key={a.id} a={a} />)}</div></div>
          </>
        )}
      </Gate>

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
      <CashBanner />
    </Screen>
  );
}
