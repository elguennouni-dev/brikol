import { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api, guarded, toast, t, L, money, fmt, useAsync, getSession } from '../session';
import { Screen, BackButton, Icon, Gate, Skeleton, catColors } from '../ui';
import { MediaGallery, uploadFile } from '../media';
import { Appointment, fmtWhen } from './Chat';

// ------------------------------------------------------------ read aloud (for people who read with difficulty)
const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window;
function speak(text) {
  try {
    const s = window.speechSynthesis;
    s.cancel();
    const ar = getSession().lang === 'ar';
    const u = new SpeechSynthesisUtterance(text);
    u.lang = ar ? 'ar-MA' : 'fr-FR';
    const voice = s.getVoices().find((v) => v.lang.toLowerCase().startsWith(ar ? 'ar' : 'fr'));
    if (voice) u.voice = voice;
    u.rate = 0.92;
    s.speak(u);
  } catch (e) { /* no voice: nothing to do */ }
}
export function SpeakButton({ text }) {
  if (!canSpeak) return null;
  return <button type="button" className="speak" onClick={() => speak(text)} aria-label={t('j_read')} title={t('j_read')}><Icon name="play" size={16} /><span>{t('j_read')}</span></button>;
}

function Modal({ onClose, children, onSubmit }) {
  return createPortal(
    <div className="modal" role="dialog" aria-modal="true" onClick={onClose}>
      <form className="modal-in col g12" onClick={(e) => e.stopPropagation()} onSubmit={(e) => { e.preventDefault(); onSubmit(); }}>{children}</form>
    </div>, document.body);
}

/** Camera / gallery button that uploads one file and gives back its id. */
function PhotoInput({ purpose, onDone, label, className = 'btn sm ghost' }) {
  const ref = useRef(null);
  const [busy, setBusy] = useState(false);
  const pick = (e) => {
    const f = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!f) return;
    setBusy(true);
    guarded(async () => { const m = await uploadFile(f, purpose); await onDone(m); }).then(() => setBusy(false));
  };
  return (
    <>
      <button type="button" className={className} disabled={busy} onClick={() => ref.current && ref.current.click()}><Icon name="camera" size={18} />{busy ? '…' : label}</button>
      <input ref={ref} type="file" accept="image/*" capture="environment" hidden onChange={pick} />
    </>
  );
}

// ------------------------------------------------------------ the agreement
function Deal({ j }) {
  const say = t('j_dealSay', { a: j.agreedPrice, e: j.extras, d: j.advances, r: j.remaining });
  return (
    <div className="card col g10 deal">
      <div className="row between g8"><b style={{ fontSize: 16 }}>{t('j_deal')}</b><SpeakButton text={say} /></div>
      <div className="dl"><span>{t('j_agreed')}</span><b className="num">{money(j.agreedPrice)}</b></div>
      {j.extras ? <div className="dl"><span>{t('j_extras')}</span><b className="num">+ {money(j.extras)}</b></div> : null}
      {j.advances ? <div className="dl"><span>{t('j_advances')}</span><b className="num">− {money(j.advances)}</b></div> : null}
      <div className="dl total"><span>{t('j_remaining')}</span><b className="num">{money(j.remaining)}</b></div>
      <span className="row g6 muted xs"><Icon name="cash" size={14} />{t('j_cash')}</span>
    </div>
  );
}

function Steps({ j }) {
  const steps = [['j_s_accepted', true], ['j_s_arrived', !!j.arrivedAt], ['j_s_finished', !!j.finishedAt], ['j_s_done', j.status === 'DONE']];
  return (
    <ol className="jsteps">
      {steps.map(([k, ok], i) => <li key={k} className={ok ? 'ok' : ''}><span className="dot">{ok ? '✓' : i + 1}</span><span>{t(k)}</span></li>)}
    </ol>
  );
}

// ------------------------------------------------------------ extras and advances
function ItemForm({ kind, onClose, send }) {
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');
  const [receipt, setReceipt] = useState(null);
  const n = parseInt(amount, 10);
  return (
    <Modal onClose={onClose} onSubmit={() => { if (n > 0) send({ kind, amount: n, label: label || null, mediaId: receipt ? receipt.id : null }); }}>
      <b style={{ fontSize: 18 }}>{t(kind === 'EXTRA' ? 'j_extraTitle' : 'j_advanceTitle')}</b>
      <label className="col g6"><span className="small muted">{t('j_amount')}</span>
        <input className="input bignum" inputMode="numeric" pattern="[0-9]*" autoFocus value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="0" />
      </label>
      <div className="row g6 wrap">
        {['j_lblMat', 'j_lblWork', 'j_lblMove'].map((k) => <button type="button" key={k} className={`chip ${label === t(k) ? 'on' : ''}`} onClick={() => setLabel(t(k))}>{t(k)}</button>)}
      </div>
      <input className="input" maxLength={120} value={label} onChange={(e) => setLabel(e.target.value)} placeholder={t('j_what')} />
      {kind === 'ADVANCE' ? (
        receipt ? <img src={receipt.url} alt="" style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 12 }} />
          : <PhotoInput purpose="RECEIPT" label={t('j_receipt')} onDone={(m) => setReceipt(m)} />
      ) : null}
      <span className="muted xs">{t(kind === 'EXTRA' ? 'j_extraHelp' : 'j_advanceHelp')}</span>
      <div className="row g8">
        <button className="btn grow" type="submit" disabled={!(n > 0)}>{t('j_send')}</button>
        <button className="btn ghost" type="button" onClick={onClose}>{t('a_cancel')}</button>
      </div>
    </Modal>
  );
}

function Items({ j, act }) {
  if (!j.items.length) return null;
  return (
    <div className="col g8">
      {j.items.map((i) => {
        const ask = i.kind === 'EXTRA' ? 'j_ask_EXTRA' : 'j_ask_ADVANCE_' + i.createdBy;
        return (
          <div key={i.id} className={`item ${i.canAnswer ? 'ask' : ''}`}>
            <div className="row between g8">
              <span className="col" style={{ gap: 2, minWidth: 0 }}>
                <b className="small">{i.canAnswer ? t(ask) : t('jk_' + i.kind)}{i.label ? ' · ' + i.label : ''}</b>
                <span className="muted xs">{i.canAnswer ? '' : (i.status === 'PENDING' ? t('j_waitOther') : t('is_' + i.status))}</span>
              </span>
              <b className="num" style={{ whiteSpace: 'nowrap' }}>{money(i.amount)}</b>
            </div>
            {i.mediaUrl ? <a href={i.mediaUrl} target="_blank" rel="noreferrer"><img src={i.mediaUrl} alt="" className="receipt" /></a> : null}
            {i.canAnswer ? (
              <div className="row g8">
                <button className="btn sm grow" onClick={() => act('/items/' + i.id + '/answer', { accept: true })}>{t('j_yes')}</button>
                <button className="btn sm ghost grow" onClick={() => act('/items/' + i.id + '/answer', { accept: false })}>{t('j_no')}</button>
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

// ------------------------------------------------------------ artisan: big buttons + code pad
function CodePad({ j, act }) {
  const [code, setCode] = useState('');
  const press = (d) => setCode((c) => (d === 'del' ? c.slice(0, -1) : (c + d).slice(0, 4)));
  if (j.codeLocked) return <div className="note wait"><Icon name="clock" size={18} />{t('j_codeLocked')}</div>;
  return (
    <div className="card col g12 codepad">
      <div className="row between g8"><b>{t('j_askCode')}</b><SpeakButton text={t('j_askCode') + '. ' + t('j_noCode')} /></div>
      <div className="codebox" dir="ltr">{[0, 1, 2, 3].map((i) => <span key={i} className={code[i] ? 'on' : ''}>{code[i] || ''}</span>)}</div>
      <div className="keys" dir="ltr">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'del', '0', 'ok'].map((k) => (
          k === 'ok'
            ? <button key={k} type="button" className="key ok" disabled={code.length !== 4} onClick={() => act('/code', { code }, () => setCode(''))}>✓</button>
            : <button key={k} type="button" className="key" onClick={() => press(k)} aria-label={k === 'del' ? '⌫' : k}>{k === 'del' ? '⌫' : k}</button>
        ))}
      </div>
      <span className="muted xs">{t('j_triesLeft', { n: j.codeTriesLeft })} · {t('j_noCode')}</span>
    </div>
  );
}

function ArtisanActions({ j, act, reloadTo }) {
  const [form, setForm] = useState(null);
  const [leave, setLeave] = useState(false);
  const withdraw = () => guarded(async () => {
    await api('/requests/' + j.requestId + '/job/withdraw', { method: 'POST' });
    toast(t('j_withdrawn'));
    reloadTo('#/jobs?tab=mine');
  });
  return (
    <>
      <div className="row between g8"><span className="muted small">{t('j_btnArrived')} · {t('j_btnExtra')} · {t('j_btnFinished')}</span><SpeakButton text={t('j_artisanSay')} /></div>
      <div className="bigbtns">
        <button className={`bigbtn ${j.arrivedAt ? 'done' : ''}`} disabled={!!j.arrivedAt} onClick={() => act('/arrived')}>
          <Icon name="pin" size={30} /><span>{t('j_btnArrived')}</span>{j.arrivedAt ? <small>✓</small> : null}
        </button>
        <button className="bigbtn" onClick={() => setForm('EXTRA')}><Icon name="plus" size={30} /><span>{t('j_btnExtra')}</span></button>
        <button className={`bigbtn main ${j.finishedAt ? 'done' : ''}`} onClick={() => act('/finished')}><Icon name="check" size={30} /><span>{t('j_btnFinished')}</span></button>
      </div>
      {j.arrivedAt && !j.finishedAt ? <span className="muted xs">✓ {t('j_arrivedOk')}</span> : null}
      {j.finishedAt ? <CodePad j={j} act={act} /> : null}
      <div className="row g12 wrap">
        <button className="link" onClick={() => setForm('ADVANCE')}>{t('j_advanceBtnA')}</button>
        {leave ? (
          <span className="col g6">
            <span className="small">{t('j_withdrawWarn')}</span>
            <span className="row g8"><button className="btn sm dark" onClick={withdraw}>{t('sure')}</button><button className="btn sm ghost" onClick={() => setLeave(false)}>{t('a_cancel')}</button></span>
          </span>
        ) : <button className="link danger" onClick={() => setLeave(true)}>{t('j_withdraw')}</button>}
      </div>
      {form ? <ItemForm kind={form} onClose={() => setForm(null)} send={(body) => act('/items', body, () => setForm(null))} /> : null}
    </>
  );
}

// ------------------------------------------------------------ client: code + confirm alone
function ClientActions({ j, act }) {
  const [show, setShow] = useState(false);
  const [form, setForm] = useState(false);
  const [sure, setSure] = useState(false);
  return (
    <>
      {j.finishedAt ? <div className="note ok"><Icon name="check" size={18} /><span>{t('j_finishedNote', { name: j.otherName })}</span></div> : null}
      <div className={`card col g10 mycode ${j.finishedAt ? 'hot' : ''}`}>
        <div className="row between g8"><b>{t('j_yourCode')}</b><SpeakButton text={t('j_codeSay') + (show ? ' ' + j.code.split('').join(' ') : '')} /></div>
        {show || j.finishedAt
          ? <div className="code num" dir="ltr">{j.code}</div>
          : <button className="btn ghost" onClick={() => setShow(true)}><Icon name="shield" size={18} />{t('j_showCode')}</button>}
        <span className="small">{t('j_codeHelp')}</span>
      </div>
      <div className="row g12 wrap">
        <button className="link" onClick={() => setForm(true)}>{t('j_advanceBtnC')}</button>
      </div>
      <div className="col g6">
        <button className="btn dark" onClick={() => { if (!sure) { setSure(true); return; } setSure(false); act('/confirm'); }}>{sure ? t('sure') : t('j_confirmAlone')}</button>
        <span className="muted xs">{t('j_confirmAloneHelp')}</span>
      </div>
      {form ? <ItemForm kind="ADVANCE" onClose={() => setForm(false)} send={(body) => act('/items', body, () => setForm(false))} /> : null}
    </>
  );
}

// ------------------------------------------------------------ photos
function Photos({ j, act }) {
  const artisan = j.myRole === 'ARTISAN';
  if (!artisan && !j.before.length && !j.after.length) return null;
  return (
    <div className="card col g10">
      <b>{t('j_photos')}</b>
      {[['BEFORE', 'j_before', j.before], ['AFTER', 'j_after', j.after]].map(([w, k, list]) => (
        <div key={w} className="col g6">
          <div className="row between g8">
            <span className="small" style={{ fontWeight: 700 }}>{t(k)}</span>
            {artisan && list.length < 6 ? <PhotoInput purpose={'JOB_' + w} label={t('j_addPhoto')} onDone={(m) => act('/photos', { mediaId: m.id, when: w })} /> : null}
          </div>
          {list.length ? <MediaGallery media={list} /> : null}
        </div>
      ))}
      {artisan ? <span className="muted xs">{t('j_photosHelp')}</span> : null}
    </div>
  );
}

// ------------------------------------------------------------ warranty
function Warranty({ j, act }) {
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState('');
  const [photo, setPhoto] = useState(null);
  const role = j.myRole;
  const claim = j.cases.find((c) => c.kind === 'WARRANTY' && c.status !== 'RESOLVED') || j.cases.filter((c) => c.kind === 'WARRANTY').slice(-1)[0];
  const active = j.warrantyUntil && new Date(j.warrantyUntil) > new Date();
  if (!active && !claim) return null;
  return (
    <div className="card col g10 warranty">
      <div className="row between g8">
        <b className="row g6"><Icon name="shield" size={18} />{t('j_warranty')}</b>
        <span className="muted xs">{active ? t('j_warrantyUntil', { d: new Date(j.warrantyUntil).toLocaleDateString(getSession().lang === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR') }) : t('j_warrantyOver')}</span>
      </div>
      <span className="small">{t('j_warrantyHelp')}</span>
      {claim ? (
        <div className={`note ${claim.status === 'RESOLVED' ? 'ok' : 'wait'}`}><span>{t('ws_' + claim.status + '_' + role)}</span></div>
      ) : null}
      {claim && claim.status === 'FIXED' && role === 'CLIENT' ? (
        <div className="row g8">
          <button className="btn sm grow" onClick={() => act('/cases/' + claim.id + '/answer', { accept: true })}>{t('j_isFixed')}</button>
          <button className="btn sm ghost grow" onClick={() => act('/cases/' + claim.id + '/answer', { accept: false })}>{t('j_notFixed')}</button>
        </div>
      ) : null}
      {claim && (claim.status === 'OPEN' || claim.status === 'ESCALATED') && role === 'ARTISAN'
        ? <button className="btn" onClick={() => act('/cases/' + claim.id + '/fixed')}>{t('j_fixedBtn')}</button> : null}
      {active && role === 'CLIENT' && (!claim || claim.status === 'RESOLVED')
        ? <button className="btn ghost" onClick={() => setOpen(true)}>{t('j_cameBack')}</button> : null}
      {open ? (
        <Modal onClose={() => setOpen(false)} onSubmit={() => act('/cases', { kind: 'WARRANTY', details: details || null, mediaId: photo ? photo.id : null }, () => setOpen(false))}>
          <b style={{ fontSize: 18 }}>{t('j_cameBack')}</b>
          <textarea className="area" rows={3} maxLength={1000} value={details} onChange={(e) => setDetails(e.target.value)} placeholder={t('j_cameBackDetails')} />
          {photo ? <img src={photo.url} alt="" style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 12 }} /> : <PhotoInput purpose="CASE" label={t('j_problemPhoto')} onDone={(m) => setPhoto(m)} />}
          <div className="row g8"><button className="btn grow" type="submit">{t('j_send')}</button><button className="btn ghost" type="button" onClick={() => setOpen(false)}>{t('a_cancel')}</button></div>
        </Modal>
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------ "I have a problem"
const REASONS = { CLIENT: ['NO_SHOW', 'PRICE', 'QUALITY', 'BEHAVIOR', 'OTHER'], ARTISAN: ['UNPAID', 'CLIENT_ABSENT', 'PRICE', 'BEHAVIOR', 'OTHER'] };
function Problem({ j, act }) {
  const [open, setOpen] = useState(null);       // preselected reason or ''
  const [details, setDetails] = useState('');
  const [photo, setPhoto] = useState(null);
  const reasons = REASONS[j.myRole].filter((r) => !(r === 'NO_SHOW' && (j.arrivedAt || j.status !== 'ACCEPTED')) && !(r === 'UNPAID' && !j.finishedAt));
  const mine = j.cases.filter((c) => c.kind === 'PROBLEM');
  const send = () => {
    if (!open) return;
    act('/cases', { kind: 'PROBLEM', reason: open, details: details || null, mediaId: photo ? photo.id : null }, () => { setOpen(null); setDetails(''); setPhoto(null); toast(t('j_problemSent')); });
  };
  const unpaid = j.myRole === 'ARTISAN' && j.finishedAt && j.doneBy !== 'CODE';
  return (
    <div className="col g8">
      {mine.length ? (
        <div className="card col g8">
          <b className="small">{t('j_myCases')}</b>
          {mine.map((c) => (
            <div key={c.id} className="col" style={{ gap: 2 }}>
              <span className="row between g8"><span className="small">{t('pr_' + c.reason)}</span><span className={`pill ${c.status === 'RESOLVED' ? 'pill-ok' : 'pill-warn'}`}>{t('cs_' + c.status)}</span></span>
              {c.resolution ? <span className="muted xs" dir="auto">{c.resolution}</span> : null}
            </div>
          ))}
        </div>
      ) : null}
      <div className="row g8 wrap">
        {unpaid ? <button className="btn sm ghost danger-b" onClick={() => setOpen('UNPAID')}><Icon name="cash" size={16} />{t('j_unpaid')}</button> : null}
        <button className="btn sm ghost danger-b" onClick={() => setOpen('')}><Icon name="flag" size={16} />{t('j_problem')}</button>
      </div>
      {open !== null ? (
        <Modal onClose={() => setOpen(null)} onSubmit={send}>
          <div className="row between g8"><b style={{ fontSize: 18 }}>{t('j_problem')}</b><SpeakButton text={t('j_problemHelp')} /></div>
          <span className="small muted">{t('j_problemHelp')}</span>
          <div className="col g6">
            {reasons.map((r) => (
              <label key={r} className={`radio ${open === r ? 'on' : ''}`}><input type="radio" name="reason" checked={open === r} onChange={() => setOpen(r)} />{t('pr_' + r)}</label>
            ))}
          </div>
          <textarea className="area" rows={3} maxLength={1000} value={details} onChange={(e) => setDetails(e.target.value)} placeholder={t('j_problemDetails')} />
          {photo ? <img src={photo.url} alt="" style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 12 }} /> : <PhotoInput purpose="CASE" label={t('j_problemPhoto')} onDone={(m) => setPhoto(m)} />}
          <div className="row g8"><button className="btn dark grow" type="submit" disabled={!open}>{t('j_send')}</button><button className="btn ghost" type="button" onClick={() => setOpen(null)}>{t('a_cancel')}</button></div>
        </Modal>
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------ screen
export default function Job({ route }) {
  const id = route.id;
  const st = useAsync(() => api('/requests/' + id + '/job'), [id], { live: true });
  const act = (path, body, after) => guarded(async () => {
    await api('/requests/' + id + '/job' + path, { method: 'POST', body });
    if (after) after();
    st.reload();
  });
  const reloadTo = (h) => { location.hash = h; };
  return (
    <Screen tab="req" width="narrow">
      <Gate st={st} loader={<Skeleton rows={4} />}>{(j) => {
        const col = catColors(j.categoryCode);
        const open = j.status === 'ACCEPTED';
        return (
          <div className="col g16 jobview">
            <div className="row g12">
              <BackButton onClick={() => { location.hash = j.myRole === 'ARTISAN' ? '#/jobs?tab=mine' : (open ? '#/requests' : '#/requests?tab=done'); }} />
              <div className="avatar" style={{ width: 44, height: 44, borderRadius: 15, background: col[0], color: col[1] }}><Icon name={j.categoryCode} size={22} /></div>
              <div className="grow col" style={{ gap: 2, minWidth: 0 }}>
                <b className="ell">{L(j, 'categoryName')}</b>
                <span className="muted xs ell">{j.otherName}{j.otherStrikes > 0 ? ' · ⚠ ' + t('j_otherStrikes', { n: j.otherStrikes }) : ''}</span>
              </div>
            </div>
            <div className="row g8">
              <a className="btn grow" href={`#/chat/${j.requestId}`}><Icon name="chatb" size={18} />{t('chat')}</a>
              {j.otherPhone ? <a className="btn ghost grow" href={`tel:${j.otherPhone}`}><Icon name="phone" size={18} />{t('call')}</a> : null}
            </div>
            <Steps j={j} />
            {j.status === 'DONE' ? (
              <div className="note ok"><Icon name="check" size={18} /><span>{t('j_done')} · {t('j_doneBy_' + (j.doneBy || 'CLIENT'))}{j.doneAt ? ' · ' + fmtWhen(j.doneAt) : ''}</span></div>
            ) : null}
            {j.status === 'DONE' && j.myRole === 'CLIENT' && !j.reviewed ? <a className="btn" href="#/requests?tab=done"><Icon name="star" size={18} />{t('j_rate')}</a> : null}
            <Deal j={j} />
            <Items j={j} act={act} />
            {open ? <Appointment requestId={j.requestId} at={j.appointmentAt} by={j.appointmentBy} confirmed={j.appointmentConfirmed} myRole={j.myRole} status={j.status} onChange={st.reload} /> : null}
            {open && j.myRole === 'ARTISAN' ? <ArtisanActions j={j} act={act} reloadTo={reloadTo} /> : null}
            {open && j.myRole === 'CLIENT' ? <ClientActions j={j} act={act} /> : null}
            {j.status === 'DONE' ? <Warranty j={j} act={act} /> : null}
            <Photos j={j} act={act} />
            {j.status === 'ACCEPTED' || j.status === 'DONE' ? <Problem j={j} act={act} /> : null}
            <span className="muted xs num" style={{ textAlign: 'center' }}>#{fmt(j.requestId)}</span>
          </div>
        );
      }}</Gate>
    </Screen>
  );
}
