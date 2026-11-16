import { useEffect, useState } from 'react';
import { api, getCats, useAsync, t, L } from '../session';
import { Icon, ArtisanCard, Screen, Loader, Empty, PageHead } from '../ui';

export default function Explore({ route }) {
  const catParam = route.q.get('cat') || '';
  const [cat, setCat] = useState(catParam);
  const [q, setQ] = useState('');
  const [dq, setDq] = useState('');
  useEffect(() => { setCat(catParam); setQ(''); setDq(''); }, [catParam]);
  useEffect(() => { const id = setTimeout(() => setDq(q), 250); return () => clearTimeout(id); }, [q]);

  const cats = useAsync(getCats, []);
  const list = useAsync(() => {
    const p = new URLSearchParams();
    if (cat) p.set('category', cat);
    if (dq.trim()) p.set('q', dq.trim());
    const qs = p.toString();
    return api('/artisans' + (qs ? '?' + qs : ''));
  }, [cat, dq]);

  return (
    <Screen tab="explore">
      <PageHead title={t('exploreTitle')} />
      <label className="field" htmlFor="q">
        <Icon name="search" size={20} />
        <input id="q" type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} autoComplete="off" />
      </label>
      <div className="chips">
        <button className={`chip ${cat ? '' : 'on'}`} onClick={() => setCat('')}>{t('all')}</button>
        {(cats.data || []).map((c) => (
          <button key={c.code} className={`chip ${cat === c.code ? 'on' : ''}`} onClick={() => setCat(c.code)}>{L(c, 'name')}</button>
        ))}
      </div>
      {list.data ? <span className="muted small" style={{ fontWeight: 600 }}>{t('results', { n: list.data.length })}</span> : null}
      <div className="agrid" style={{ opacity: list.loading && list.data ? 0.55 : 1, transition: 'opacity .15s' }}>
        {list.error ? <Empty onRetry={list.reload}>{t('err_GENERIC')}</Empty>
          : !list.data ? <Loader />
          : list.data.length ? list.data.map((a) => <ArtisanCard key={a.id} a={a} />)
          : <div className="empty">{t('none')}</div>}
      </div>
    </Screen>
  );
}
