import { useAsync, useNotifs, refreshNotifs, markAllRead, t, back } from '../session';
import { Screen, BackButton, NotifItem, Empty, Skeleton } from '../ui';

export default function Notifications() {
  const { items, unread, live } = useNotifs();
  const st = useAsync(async () => { await refreshNotifs(); return true; }, []);
  return (
    <Screen tab="alerts" width="narrow">
      <div className="row between g12">
        <div className="row g12"><BackButton onClick={back} /><h1 className="h1" style={{ fontSize: 22 }}>{t('notifTitle')}</h1>{live ? <span className="pill pill-ok">{t('notifLive')}</span> : null}</div>
        {unread > 0 ? <button className="link" onClick={markAllRead}>{t('notifReadAll')}</button> : null}
      </div>
      {items.length ? <div className="card nlist">{items.map((n) => <NotifItem key={n.id} n={n} />)}</div>
        : st.data ? <Empty>{t('notifEmpty')}</Empty> : <Skeleton rows={4} />}
    </Screen>
  );
}
