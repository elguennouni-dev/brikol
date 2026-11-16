// Geocoding with Photon (open source, OpenStreetMap data, made for search-as-you-type).
const PHOTON = 'https://photon.komoot.io';
const MOROCCO_BBOX = '-17.3,20.7,-0.9,36.0';
export const DEFAULT_CENTER = { lat: 33.5731, lng: -7.5898 }; // Casablanca
export const CITY_CENTERS = {
  casablanca: [33.5731, -7.5898], rabat: [34.0209, -6.8416], marrakech: [31.6295, -7.9811], fes: [34.0181, -5.0078],
  tanger: [35.7595, -5.834], agadir: [30.4278, -9.5981], meknes: [33.8935, -5.5473], oujda: [34.6814, -1.9086],
  kenitra: [34.261, -6.5802], tetouan: [35.5785, -5.3684], sale: [34.0531, -6.7985], mohammedia: [33.6861, -7.3829], eljadida: [33.2316, -8.5007]
};
export const cityKey = (c) => String(c || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z]/g, '')
  .replace(/^marrakesh$/, 'marrakech').replace(/^fez$/, 'fes').replace(/^tangier$/, 'tanger');
export function centerOf(city) {
  const c = CITY_CENTERS[cityKey(city)];
  return c ? { lat: c[0], lng: c[1] } : DEFAULT_CENTER;
}

function toPlace(f) {
  const p = f.properties || {};
  const [lng, lat] = f.geometry.coordinates;
  const street = [p.housenumber, p.street].filter(Boolean).join(' ');
  const main = p.name && p.name !== p.city ? p.name : (street || p.district || p.locality || p.city || p.county || '');
  const hood = p.district || p.locality || p.suburb || '';
  const city = p.city || p.county || p.state || '';
  const second = [street && street !== main ? street : '', hood && hood !== main ? hood : '', city && city !== main ? city : ''].filter(Boolean).join(', ');
  return { lat, lng, title: main || city, sub: second, address: [main, second].filter(Boolean).join(', ').slice(0, 200), hood: hood.slice(0, 80), city: city.slice(0, 80) };
}

export async function searchPlaces(q, near, signal) {
  const u = `${PHOTON}/api/?q=${encodeURIComponent(q)}&lang=fr&limit=7&bbox=${MOROCCO_BBOX}` + (near ? `&lat=${near.lat}&lon=${near.lng}` : '');
  const res = await fetch(u, { signal });
  if (!res.ok) throw new Error('search');
  const j = await res.json();
  return (j.features || []).map(toPlace);
}

export async function reversePlace(lat, lng, signal) {
  const res = await fetch(`${PHOTON}/reverse?lat=${lat}&lon=${lng}&lang=fr&limit=1`, { signal });
  if (!res.ok) throw new Error('reverse');
  const j = await res.json();
  return j.features && j.features.length ? { ...toPlace(j.features[0]), lat, lng } : { lat, lng, title: '', sub: '', address: '', hood: '', city: '' };
}

export function currentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject({ code: 'GEO_UNAVAILABLE' }); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      (e) => reject({ code: e && e.code === 1 ? 'GEO_DENIED' : 'GEO_UNAVAILABLE' }),
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 }
    );
  });
}

export const directionsUrl = (lat, lng) => `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
export const osmUrl = (lat, lng) => `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=17/${lat}/${lng}`;
