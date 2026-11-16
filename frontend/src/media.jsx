import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { getSession, t, toast, errMsg } from './session';
import { Icon } from './ui';

// ------------------------------------------------------------ upload
const IMG_MAX = 1600;

// Photos are resized in the browser (phones take 4000px photos): faster upload, less storage.
async function shrinkImage(file) {
  if (!/^image\/(jpeg|png|webp|heic|heif)$/i.test(file.type) && !/\.(heic|heif)$/i.test(file.name || '')) return file;
  try {
    const bmp = await createImageBitmap(file);
    const scale = Math.min(1, IMG_MAX / Math.max(bmp.width, bmp.height));
    if (scale === 1 && file.type === 'image/jpeg' && file.size < 1.5e6) return file;
    const c = document.createElement('canvas');
    c.width = Math.round(bmp.width * scale);
    c.height = Math.round(bmp.height * scale);
    c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
    const blob = await new Promise((res) => c.toBlob(res, 'image/jpeg', 0.82));
    return blob ? new File([blob], 'photo.jpg', { type: 'image/jpeg' }) : file;
  } catch (e) {
    return file; // the server decides (HEIC not decodable here, for example)
  }
}

/** Uploads one file; onProgress(0..1). Resolves to { id, kind, mime, url }. */
export async function uploadFile(file, purpose, onProgress) {
  const f = file.type.startsWith('image/') || /\.(heic|heif)$/i.test(file.name || '') ? await shrinkImage(file) : file;
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/uploads');
    const tok = getSession().token;
    if (tok) xhr.setRequestHeader('Authorization', 'Bearer ' + tok);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total); };
    xhr.onload = () => {
      let data = null;
      try { data = JSON.parse(xhr.responseText); } catch (e) { /* empty */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data);
      else reject({ status: xhr.status, code: (data && data.code) || (xhr.status === 413 ? 'FILE_TOO_BIG' : 'UPLOAD_FAILED') });
    };
    xhr.onerror = () => reject({ code: 'NETWORK' });
    const fd = new FormData();
    fd.append('file', f, f.name || 'file');
    fd.append('purpose', purpose);
    xhr.send(fd);
  });
}

// ------------------------------------------------------------ private images (ID card): fetched with the token
export function PrivateImg({ src, alt = '', className = '', onClick }) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    if (!src) return undefined;
    let live = true;
    let made = null;
    if (!src.startsWith('/api/')) { setUrl(src); return undefined; }
    fetch(src, { headers: { Authorization: 'Bearer ' + getSession().token } })
      .then((r) => (r.ok ? r.blob() : null))
      .then((b) => { if (b && live) { made = URL.createObjectURL(b); setUrl(made); } })
      .catch(() => {});
    return () => { live = false; if (made) URL.revokeObjectURL(made); };
  }, [src]);
  return url ? <img src={url} alt={alt} className={className} onClick={onClick ? () => onClick(url) : undefined} /> : <span className={`skel ${className}`} />;
}

// ------------------------------------------------------------ viewer
export function Lightbox({ src, onClose }) {
  useEffect(() => {
    const k = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', k);
    return () => document.removeEventListener('keydown', k);
  }, [onClose]);
  return createPortal(
    <div className="lightbox" role="dialog" aria-modal="true" onClick={onClose}>
      <img src={src} alt="" onClick={(e) => e.stopPropagation()} />
      <button className="lb-x" onClick={onClose} aria-label={t('a_cancel')}>×</button>
    </div>, document.body);
}

/** Photos (tap to enlarge), videos and voice notes of a request. */
export function MediaGallery({ media }) {
  const [big, setBig] = useState(null);
  if (!media || !media.length) return null;
  const imgs = media.filter((m) => m.kind === 'IMAGE');
  const vids = media.filter((m) => m.kind === 'VIDEO');
  const auds = media.filter((m) => m.kind === 'AUDIO');
  return (
    <div className="col g8">
      {imgs.length ? (
        <div className="mgrid">
          {imgs.map((m) => <button key={m.id} type="button" className="mthumb" onClick={() => setBig(m.url)}><img src={m.url} alt="" loading="lazy" /></button>)}
        </div>
      ) : null}
      {vids.map((m) => <video key={m.id} className="mvideo" src={m.url} controls preload="metadata" playsInline />)}
      {auds.map((m) => <div key={m.id} className="maudio"><Icon name="mic" size={18} /><audio src={m.url} controls preload="metadata" /></div>)}
      {big ? <Lightbox src={big} onClose={() => setBig(null)} /> : null}
    </div>
  );
}

// ------------------------------------------------------------ voice recorder
const AUDIO_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/aac'];
const MAX_SECONDS = 120;

function Recorder({ onDone, onCancel }) {
  const [sec, setSec] = useState(0);
  const rec = useRef(null);
  const chunks = useRef([]);
  const stream = useRef(null);
  const cancelled = useRef(false);

  useEffect(() => {
    let timer;
    (async () => {
      try {
        const s = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.current = s;
        const type = (window.MediaRecorder && AUDIO_TYPES.find((x) => MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(x))) || '';
        const r = new MediaRecorder(s, type ? { mimeType: type } : undefined);
        rec.current = r;
        r.ondataavailable = (e) => { if (e.data && e.data.size) chunks.current.push(e.data); };
        r.onstop = () => {
          s.getTracks().forEach((tr) => tr.stop());
          if (cancelled.current) return;
          const mime = (r.mimeType || type || 'audio/webm').split(';')[0];
          const ext = mime.includes('mp4') ? 'm4a' : mime.includes('ogg') ? 'ogg' : mime.includes('aac') ? 'aac' : 'weba';
          onDone(new File(chunks.current, 'voice.' + ext, { type: mime }));
        };
        r.start(1000);
        timer = setInterval(() => setSec((x) => {
          if (x + 1 >= MAX_SECONDS && r.state === 'recording') r.stop();
          return x + 1;
        }), 1000);
      } catch (e) {
        toast(errMsg({ code: 'MIC_DENIED' }));
        onCancel();
      }
    })();
    return () => {
      clearInterval(timer);
      if (rec.current && rec.current.state === 'recording') { cancelled.current = true; rec.current.stop(); }
      if (stream.current) stream.current.getTracks().forEach((tr) => tr.stop());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stop = () => { if (rec.current && rec.current.state === 'recording') rec.current.stop(); };
  const cancel = () => { cancelled.current = true; stop(); onCancel(); };
  const mm = String(Math.floor(sec / 60)).padStart(1, '0') + ':' + String(sec % 60).padStart(2, '0');
  return (
    <div className="recbar" role="status">
      <span className="recdot" /><b className="num">{mm}</b><span className="muted xs grow">{t('recording')}</span>
      <button type="button" className="link danger" onClick={cancel}>{t('a_cancel')}</button>
      <button type="button" className="btn sm dark" onClick={stop}><Icon name="checkMark" size={16} />{t('recStop')}</button>
    </div>
  );
}

// ------------------------------------------------------------ picker used in the new request form
/** items: [{ key, id?, kind, url?, preview?, progress, error? }] — owned by the parent (kept in the draft). */
export function MediaPicker({ items, setItems, max = 6 }) {
  const input = useRef(null);
  const [recording, setRecording] = useState(false);
  const canRecord = typeof window !== 'undefined' && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder);
  const full = items.length >= max;

  const add = (file) => {
    const kind = file.type.startsWith('video/') ? 'VIDEO' : file.type.startsWith('audio/') ? 'AUDIO' : 'IMAGE';
    const key = Math.random().toString(36).slice(2);
    const preview = kind === 'IMAGE' || kind === 'VIDEO' ? URL.createObjectURL(file) : null;
    setItems((list) => [...list, { key, kind, preview, progress: 0 }]);
    const patch = (p) => setItems((list) => list.map((x) => (x.key === key ? { ...x, ...p } : x)));
    uploadFile(file, 'REQUEST', (p) => patch({ progress: p }))
      .then((m) => patch({ id: m.id, url: m.url, progress: 1 }))
      .catch((e) => { toast(errMsg(e)); setItems((list) => list.filter((x) => x.key !== key)); });
  };
  const onFiles = (e) => {
    const files = Array.from(e.target.files || []);
    e.target.value = '';
    files.slice(0, max - items.length).forEach(add);
  };
  const remove = (key) => setItems((list) => list.filter((x) => x.key !== key));

  return (
    <div className="col g10">
      <div className="row g8 wrap">
        <button type="button" className="mbtn" disabled={full} onClick={() => input.current.click()}><Icon name="camera" size={20} />{t('addMedia')}</button>
        {canRecord ? <button type="button" className="mbtn" disabled={full || recording} onClick={() => setRecording(true)}><Icon name="mic" size={20} />{t('recVoice')}</button> : null}
        <input ref={input} type="file" accept="image/*,video/*" multiple hidden onChange={onFiles} />
      </div>
      {recording ? <Recorder onDone={(f) => { setRecording(false); add(f); }} onCancel={() => setRecording(false)} /> : null}
      {items.length ? (
        <div className="mgrid">
          {items.map((x) => (
            <div key={x.key} className={`mthumb ${x.id ? '' : 'up'}`}>
              {x.kind === 'IMAGE' ? <img src={x.preview || x.url} alt="" /> : null}
              {x.kind === 'VIDEO' ? <><video src={x.preview || x.url} muted playsInline preload="metadata" /><span className="mtag"><Icon name="play" size={14} /></span></> : null}
              {x.kind === 'AUDIO' ? <span className="maud"><Icon name="mic" size={22} /><span className="xs">{t('voiceNote')}</span></span> : null}
              {!x.id ? <span className="mprog"><i style={{ width: Math.round((x.progress || 0) * 100) + '%' }} /></span> : null}
              <button type="button" className="mdel" onClick={() => remove(x.key)} aria-label={t('a_delete')}>×</button>
            </div>
          ))}
        </div>
      ) : <span className="muted xs">{t('mediaHint', { n: max })}</span>}
    </div>
  );
}

/** Simple image upload slot (avatar, ID card, portfolio) used in the artisan file. */
export function ImageSlot({ label, purpose, value, onUploaded, privateSrc = false, round = false }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(0);
  const pick = (e) => {
    const f = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!f) return;
    setBusy(0.01);
    uploadFile(f, purpose, (p) => setBusy(Math.max(0.01, p)))
      .then((m) => { setBusy(0); onUploaded(m); })
      .catch((err) => { setBusy(0); toast(errMsg(err)); });
  };
  return (
    <button type="button" className={`islot ${round ? 'round' : ''} ${value ? 'has' : ''}`} onClick={() => input.current.click()}>
      {value ? (privateSrc ? <PrivateImg src={value.url} /> : <img src={value.url} alt="" />) : <span className="col" style={{ alignItems: 'center', gap: 4 }}><Icon name="camera" size={22} /><span className="xs">{label}</span></span>}
      {busy ? <span className="mprog"><i style={{ width: Math.round(busy * 100) + '%' }} /></span> : null}
      <input ref={input} type="file" accept="image/*" hidden onChange={pick} />
    </button>
  );
}
