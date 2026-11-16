import { useEffect, useRef, useState } from 'react';
import { api, guarded, toast, t, L, back, onChatMessage, setOpenChat, getSession, useAsync } from '../session';
import { Screen, BackButton, Icon, Loader, Empty } from '../ui';
import { ReportButton } from '../report';

export const fmtWhen = (iso) => new Date(iso).toLocaleString(getSession().lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR',
  { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

// datetime-local value <-> ISO
const toLocalInput = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

/** Appointment block (chat header and request cards). */
export function Appointment({ requestId, at, by, confirmed, myRole, status, onChange }) {
  const [edit, setEdit] = useState(false);
  const [val, setVal] = useState(() => toLocalInput(new Date(Date.now() + 24 * 3600e3)));
  const propose = () => guarded(async () => {
    const d = new Date(val);
    if (Number.isNaN(d.getTime())) throw { code: 'APPOINTMENT_INVALID' };
    await api('/requests/' + requestId + '/appointment', { method: 'POST', body: { at: d.toISOString() } });
    toast(t('apptSent'));
    setEdit(false);
    onChange();
  });
  const confirm = () => guarded(async () => { await api('/requests/' + requestId + '/appointment/confirm', { method: 'POST' }); toast(t('apptConfirmed')); onChange(); });
  if (status !== 'ACCEPTED' && !at) return null;
  return (
    <div className={`appt ${confirmed ? 'ok' : ''}`}>
      <div className="row g8" style={{ alignItems: 'center' }}>
        <Icon name="clock" size={18} />
        <span className="grow">
          {at ? <><b>{fmtWhen(at)}</b> · {confirmed ? t('apptOk') : (by === myRole ? t('apptWaitOther') : t('apptToConfirm'))}</> : t('apptNone')}
        </span>
      </div>
      {status === 'ACCEPTED' ? (
        edit ? (
          <div className="row g8 wrap">
            <input className="input" style={{ flex: '1 1 200px', minWidth: 0 }} type="datetime-local" value={val} min={toLocalInput(new Date(Date.now() + 3600e3))} onChange={(e) => setVal(e.target.value)} />
            <button className="btn sm" onClick={propose}>{t('propose')}</button>
            <button className="btn sm ghost" onClick={() => setEdit(false)}>{t('a_cancel')}</button>
          </div>
        ) : (
          <div className="row g8 wrap">
            {at && !confirmed && by !== myRole ? <button className="btn sm" onClick={confirm}>{t('apptConfirm')}</button> : null}
            <button className="btn sm ghost" onClick={() => setEdit(true)}>{at ? t('apptOther') : t('apptPropose')}</button>
          </div>
        )
      ) : null}
    </div>
  );
}

export default function Chat({ route }) {
  const id = route.id;
  const st = useAsync(() => api('/requests/' + id + '/messages'), [id]);
  const [msgs, setMsgs] = useState([]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const end = useRef(null);

  useEffect(() => { if (st.data) setMsgs(st.data.messages); }, [st.data]);
  useEffect(() => {
    setOpenChat(id);
    const off = onChatMessage((m) => { if (String(m.requestId) === String(id)) { setMsgs((list) => (list.some((x) => x.id === m.id) ? list : [...list, m])); api('/requests/' + id + '/messages').catch(() => {}); } });
    return () => { setOpenChat(null); off(); };
  }, [id]);
  useEffect(() => { if (end.current) end.current.scrollIntoView({ block: 'end' }); }, [msgs.length]);

  if (st.error) return <Screen tab="req" width="narrow"><Empty onRetry={st.reload}>{t('err_REQUEST_NOT_FOUND')}</Empty></Screen>;
  if (!st.data) return <Screen tab="req" width="narrow"><Loader /></Screen>;
  const c = st.data;
  const send = (e) => {
    e.preventDefault();
    const body = text.trim();
    if (!body) return;
    setBusy(true);
    guarded(async () => {
      const m = await api('/requests/' + id + '/messages', { method: 'POST', body: { body } });
      setMsgs((list) => [...list, m]);
      setText('');
    }).then(() => setBusy(false));
  };
  return (
    <Screen tab="req" width="narrow" nav={false}>
      <div className="row g12 chat-head">
        <BackButton onClick={back} />
        <div className="col grow" style={{ minWidth: 0, gap: 0 }}>
          <b className="ell" style={{ fontSize: 17 }}>{c.otherName}</b>
          <span className="muted xs">{L(c, 'categoryName')} · #{c.requestId}</span>
        </div>
        {c.otherPhone ? <a className="iconbtn" href={`tel:${c.otherPhone}`} aria-label={t('call')}><Icon name="phone" size={18} /></a> : null}
      </div>
      <Appointment requestId={c.requestId} at={c.appointmentAt} by={c.appointmentBy} confirmed={c.appointmentConfirmed} myRole={c.myRole} status={c.status} onChange={st.reload} />
      <div className="chat">
        {msgs.length ? msgs.map((m) => (
          <div key={m.id} className={`bubble ${m.mine ? 'mine' : ''}`}>
            <span dir="auto">{m.body}</span>
            <small>
              {!m.mine ? <ReportButton targetType="MESSAGE" targetId={m.id} iconOnly className="bubble-flag" /> : null}
              {new Date(m.createdAt).toLocaleTimeString(getSession().lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { hour: '2-digit', minute: '2-digit' })}{m.mine && m.read ? ' ✓✓' : ''}
            </small>
          </div>
        )) : <div className="muted small" style={{ textAlign: 'center', padding: 24 }}>{t('chatEmpty')}</div>}
        <div ref={end} />
      </div>
      {c.status === 'ACCEPTED' || c.status === 'DONE' ? (
        <form className="chat-input" onSubmit={send}>
          <textarea rows={1} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} placeholder={t('chatPh')} aria-label={t('chatPh')}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(e); } }} />
          <button className="btn sm" type="submit" disabled={busy || !text.trim()} aria-label={t('send')}><Icon name="send" size={18} /></button>
        </form>
      ) : null}
    </Screen>
  );
}
