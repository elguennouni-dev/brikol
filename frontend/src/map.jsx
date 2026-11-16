import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './map.css';
import { t, toast, errMsg } from './session';
import { Icon } from './ui';
import { searchPlaces, reversePlace, currentPosition } from './geo';

const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIB = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';

function makeMap(el, center, zoom, interactive) {
  const map = L.map(el, {
    center: [center.lat, center.lng], zoom, zoomControl: false, attributionControl: true,
    dragging: interactive, scrollWheelZoom: interactive, doubleClickZoom: interactive, touchZoom: interactive ? 'center' : false,
    boxZoom: false, keyboard: interactive, tap: false
  });
  map.attributionControl.setPrefix(false);
  L.tileLayer(TILES, { maxZoom: 19, attribution: ATTRIB }).addTo(map);
  if (interactive) L.control.zoom({ position: 'bottomright' }).addTo(map);
  return map;
}

// Small read-only map. approx = draws a circle instead of a pin (place hidden until the offer is accepted).
export function MapPreview({ lat, lng, approx = false, height = 150, radiusKm = null }) {
  const ref = useRef(null);
  useEffect(() => {
    const map = makeMap(ref.current, { lat, lng }, approx ? 14 : 16, false);
    if (approx) L.circle([lat, lng], { radius: 700, color: '#0E2B22', weight: 1.5, fillColor: '#CDEE3B', fillOpacity: 0.35 }).addTo(map);
    else L.marker([lat, lng], { icon: L.divIcon({ className: 'pin-ic', html: '<span></span>', iconSize: [28, 38], iconAnchor: [14, 36] }) }).addTo(map);
    if (radiusKm) {
      const c = L.circle([lat, lng], { radius: radiusKm * 1000, color: '#0E2B22', weight: 1.5, fillColor: '#CDEE3B', fillOpacity: 0.2 }).addTo(map);
      map.fitBounds(c.getBounds(), { padding: [8, 8] });
    }
    return () => map.remove();
  }, [lat, lng, approx, radiusKm]);
  return <div ref={ref} className="mapbox" style={{ height }} dir="ltr" />;
}

// Full-screen picker (inDrive style): the map moves under a fixed pin; search with suggestions; "my location".
export function LocationSheet({ start, onPick, onClose, autoLocate }) {
  const mapEl = useRef(null);
  const map = useRef(null);
  const ctl = useRef(null);
  const chosen = useRef(null); // a search result keeps its own name when the map lands on it
  const [place, setPlace] = useState(start && start.address ? start : null);
  const [moving, setMoving] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [q, setQ] = useState('');
  const [hits, setHits] = useState([]);
  const [searching, setSearching] = useState(false);
  const [locating, setLocating] = useState(false);

  const resolve = (c) => {
    if (ctl.current) ctl.current.abort();
    const ac = new AbortController();
    ctl.current = ac;
    setResolving(true);
    reversePlace(+c.lat.toFixed(6), +c.lng.toFixed(6), ac.signal)
      .then((p) => { if (!ac.signal.aborted) setPlace(p); })
      .catch(() => { if (!ac.signal.aborted) setPlace({ lat: c.lat, lng: c.lng, title: '', sub: '', address: '', hood: '', city: '' }); })
      .finally(() => { if (!ac.signal.aborted) setResolving(false); });
  };

  const locate = (quiet) => {
    setLocating(true);
    currentPosition()
      .then((p) => { if (map.current) map.current.setView([p.lat, p.lng], 17); })
      .catch((e) => { if (!quiet) toast(errMsg(e)); })
      .finally(() => setLocating(false));
  };

  useEffect(() => {
    const c = start || { lat: 33.5731, lng: -7.5898 };
    const m = makeMap(mapEl.current, c, start && start.address ? 17 : 13, true);
    map.current = m;
    m.on('movestart', () => setMoving(true));
    m.on('moveend', () => {
      setMoving(false);
      const h = chosen.current;
      chosen.current = null;
      if (h && m.getCenter().distanceTo([h.lat, h.lng]) < 30) { if (ctl.current) ctl.current.abort(); setResolving(false); setPlace(h); } else resolve(m.getCenter());
    });
    if (!(start && start.address)) resolve(m.getCenter());
    if (autoLocate) locate(true);
    const esc = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', esc);
    document.body.classList.add('sheet-open');
    return () => { document.removeEventListener('keydown', esc); document.body.classList.remove('sheet-open'); if (ctl.current) ctl.current.abort(); m.remove(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // search as you type (debounced, previous request cancelled)
  useEffect(() => {
    const text = q.trim();
    if (text.length < 3) { setHits([]); setSearching(false); return undefined; }
    const ac = new AbortController();
    setSearching(true);
    const timer = setTimeout(() => {
      const c = map.current ? map.current.getCenter() : null;
      searchPlaces(text, c, ac.signal).then((r) => { setHits(r); setSearching(false); }).catch(() => { if (!ac.signal.aborted) { setHits([]); setSearching(false); } });
    }, 350);
    return () => { clearTimeout(timer); ac.abort(); };
  }, [q]);

  const choose = (h) => {
    setQ('');
    setHits([]);
    chosen.current = h;
    if (map.current) map.current.setView([h.lat, h.lng], 17);
  };

  const confirm = () => {
    if (!place) return;
    onPick({ lat: place.lat, lng: place.lng, address: place.address || `${place.lat.toFixed(5)}, ${place.lng.toFixed(5)}`, hood: place.hood, city: place.city });
  };

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label={t('mapTitle')}>
      <div className="sheet-in">
        <div className="sheet-top">
          <button className="iconbtn" onClick={onClose} aria-label={t('back')}><Icon name="back" size={20} className="flip" /></button>
          <div className="msearch">
            <Icon name="search" size={18} />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('mapSearch')} aria-label={t('mapSearch')} autoComplete="off" enterKeyHint="search" />
            {q ? <button className="mclear" onClick={() => setQ('')} aria-label={t('a_cancel')}>×</button> : null}
          </div>
          {q.trim().length >= 3 ? (
            <div className="mhits" role="listbox">
              {hits.map((h, i) => (
                <button key={i} className="mhit" role="option" aria-selected="false" onClick={() => choose(h)}>
                  <Icon name="pin" size={18} /><span className="col" style={{ minWidth: 0 }}><b className="ell">{h.title}</b><span className="muted xs ell">{h.sub}</span></span>
                </button>
              ))}
              {!hits.length ? <div className="muted small" style={{ padding: 14 }}>{searching ? t('loading') : t('mapNoResult')}</div> : null}
            </div>
          ) : null}
        </div>
        <div className="mapwrap">
          <div ref={mapEl} className="mapbox full" dir="ltr" />
          <div className={`cpin ${moving ? 'up' : ''}`} aria-hidden="true"><span className="head" /><span className="stick" /><span className="shadow" /></div>
          <button className={`locbtn ${locating ? 'busy' : ''}`} onClick={() => locate(false)} aria-label={t('mapMe')}><Icon name="target" size={22} /></button>
        </div>
        <div className="sheet-bot">
          <div className="row g12" style={{ minWidth: 0 }}>
            <span className="pdot"><Icon name="pin" size={20} /></span>
            <span className="col grow" style={{ minWidth: 0, gap: 2 }}>
              <span className="muted xs">{t('mapHere')}</span>
              <b className="ell">{moving || resolving ? t('loading') : (place && place.title) || t('mapUnnamed')}</b>
              {!moving && !resolving && place && place.sub ? <span className="muted xs ell">{place.sub}</span> : null}
            </span>
          </div>
          <button className="btn block" onClick={confirm} disabled={!place || moving || resolving}>{t('mapConfirm')}</button>
        </div>
      </div>
    </div>
  );
}
