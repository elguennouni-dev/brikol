import { lazy, Suspense } from 'react';
import { createPortal } from 'react-dom';
import { t } from './session';
import { Icon } from './ui';
import { directionsUrl, osmUrl } from './geo';

// Leaflet is only downloaded when a map is shown.
const mod = () => import('./map');
const LazyPreview = lazy(() => mod().then((m) => ({ default: m.MapPreview })));
const LazySheet = lazy(() => mod().then((m) => ({ default: m.LocationSheet })));

export function MapPreview(props) {
  return <Suspense fallback={<div className="mapbox skel" style={{ height: props.height || 150 }} />}><LazyPreview {...props} /></Suspense>;
}
export function LocationSheet(props) {
  // Rendered on <body>: an animated (transformed) parent would otherwise trap position:fixed.
  return createPortal(<Suspense fallback={<div className="sheet" />}><LazySheet {...props} /></Suspense>, document.body);
}

// Address line + map of a request. exact = false for artisans before acceptance (circle, no street).
export function RequestPlace({ r, withMap = true, directions = false }) {
  if (r.lat == null || r.lng == null) return null;
  const exact = !!r.address;
  return (
    <div className="col g8">
      {withMap ? <MapPreview lat={r.lat} lng={r.lng} approx={!exact} height={140} /> : null}
      <div className="placeline">
        <Icon name="pin" size={15} />
        <span className="grow ell">{exact ? r.address : t('placeApprox')}</span>
        {exact ? <a href={directions ? directionsUrl(r.lat, r.lng) : osmUrl(r.lat, r.lng)} target="_blank" rel="noopener">{directions ? t('directions') : t('seeMap')}</a> : null}
      </div>
    </div>
  );
}
