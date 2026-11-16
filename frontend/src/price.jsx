import { useState } from 'react';
import { t, money, fmt } from './session';

export const inRange = (v, min, max) => Number.isFinite(v) && v >= min && v <= max;
export const toInt = (s) => parseInt(String(s).replace(/[^\d]/g, ''), 10);

/** Price typed by hand, with the allowed range under it. */
export function PriceField({ value, onChange, min, max, label, autoFocus = false }) {
  const n = toInt(value);
  const bad = value !== '' && !inRange(n, min, max);
  return (
    <div className="col g6">
      <div className={`pricein ${bad ? 'bad' : ''}`}>
        <input type="text" inputMode="numeric" pattern="[0-9]*" value={value} autoFocus={autoFocus}
          onChange={(e) => onChange(e.target.value.replace(/[^\d]/g, '').slice(0, 7))} placeholder={label || t('yourPrice')} aria-label={label || t('yourPrice')} />
        <b className="muted">{t('dh')}</b>
      </div>
      <span className={`xs ${bad ? '' : 'muted'}`} style={bad ? { color: 'var(--red)', fontWeight: 700 } : null}>
        {t('priceRange', { min: money(min), max: money(max) })}
      </span>
    </div>
  );
}

/** Inline "propose another price" form used in the negotiation (client and artisan). */
export function CounterForm({ min, max, current, onSend, onCancel }) {
  const [v, setV] = useState('');
  const n = toInt(v);
  const ok = inRange(n, min, max) && n !== current;
  return (
    <form className="col g8" onSubmit={(e) => { e.preventDefault(); if (ok) onSend(n); }}>
      <PriceField value={v} onChange={setV} min={min} max={max} label={t('newPricePh')} autoFocus />
      <div className="row g8">
        <button className="btn sm" type="submit" disabled={!ok}>{t('propose')} {ok ? fmt(n) + ' ' + t('dh') : ''}</button>
        <button className="btn sm ghost" type="button" onClick={onCancel}>{t('a_cancel')}</button>
      </div>
    </form>
  );
}
