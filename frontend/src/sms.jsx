import { useEffect, useState } from 'react';
import { api, t, getSession } from './session';

/** Sends an SMS code and counts down before it can be sent again. */
export function useSmsCode(purpose) {
  const [sentTo, setSentTo] = useState(null);
  const [code, setCode] = useState('');
  const [wait, setWait] = useState(0);
  useEffect(() => {
    if (wait <= 0) return undefined;
    const timer = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(timer);
  }, [wait]);
  const send = async (phone) => {
    await api('/auth/otp', { method: 'POST', body: { phone, purpose, lang: getSession().lang } });
    setSentTo(phone);
    setCode('');
    setWait(60);
  };
  return { sentTo, code, setCode, wait, send, reset: () => { setSentTo(null); setCode(''); } };
}

export function CodeField({ sms, phone, onResend }) {
  return (
    <div className="col g8">
      <label className="lbl" htmlFor="otp" style={{ marginBottom: 0 }}>{t('otpLabel')}</label>
      <span className="muted xs">{t('otpSent', { p: phone })}</span>
      <input id="otp" className="input otp" inputMode="numeric" autoComplete="one-time-code" maxLength={6} dir="ltr"
        value={sms.code} onChange={(e) => sms.setCode(e.target.value.replace(/[^\d]/g, '').slice(0, 6))} placeholder="••••••" />
      <button type="button" className="link" style={{ alignSelf: 'flex-start' }} disabled={sms.wait > 0} onClick={onResend}>
        {sms.wait > 0 ? t('otpResendIn', { n: sms.wait }) : t('otpResend')}
      </button>
    </div>
  );
}
