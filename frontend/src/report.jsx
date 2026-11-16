import { useState } from 'react';
import { createPortal } from 'react-dom';
import { api, guarded, toast, t, useSession } from './session';
import { Icon } from './ui';

const REASONS = ['NO_SHOW', 'FRAUD', 'RUDE', 'BAD_WORK', 'PRICE', 'FAKE_PROFILE', 'SPAM', 'OTHER'];

/** "Report" link + dialog. targetType: ARTISAN (profile id), USER, REQUEST, REVIEW or MESSAGE. */
export function ReportButton({ targetType, targetId, label, className = 'link muted', iconOnly = false }) {
  const { token } = useSession();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [details, setDetails] = useState('');
  const [busy, setBusy] = useState(false);
  if (!token) return null;
  const send = (e) => {
    e.preventDefault();
    if (!reason) return;
    setBusy(true);
    guarded(async () => {
      await api('/reports', { method: 'POST', body: { targetType, targetId, reason, details: details || null } });
      toast(t('reportSent'));
      setOpen(false);
      setReason('');
      setDetails('');
    }).then(() => setBusy(false));
  };
  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)} aria-label={t('report')} title={t('report')}><Icon name="flag" size={14} />{iconOnly ? null : ' ' + (label || t('report'))}</button>
      {open ? createPortal(
        <div className="modal" role="dialog" aria-modal="true" onClick={() => setOpen(false)}>
          <form className="modal-in col g12" onClick={(e) => e.stopPropagation()} onSubmit={send}>
            <b style={{ fontSize: 18 }}>{t('reportTitle')}</b>
            <div className="col g6">
              {REASONS.map((r) => (
                <label key={r} className={`radio ${reason === r ? 'on' : ''}`}>
                  <input type="radio" name="reason" value={r} checked={reason === r} onChange={() => setReason(r)} />{t('rr_' + r)}
                </label>
              ))}
            </div>
            <textarea className="area" rows={3} maxLength={1000} value={details} onChange={(e) => setDetails(e.target.value)} placeholder={t('reportDetails')} />
            <div className="row g8">
              <button className="btn sm dark" type="submit" disabled={!reason || busy}>{t('reportSend')}</button>
              <button className="btn sm ghost" type="button" onClick={() => setOpen(false)}>{t('a_cancel')}</button>
            </div>
          </form>
        </div>, document.body) : null}
    </>
  );
}
