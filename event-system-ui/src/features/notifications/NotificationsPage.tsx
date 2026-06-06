import { useNotificationStore } from '../../notifications/notificationStore';
import { formatDateTime } from '../../lib/format';
import '../../components/common.css';

export function NotificationsPage() {
    const items = useNotificationStore((s) => s.items);
    const clear = useNotificationStore((s) => s.clear);

    return (
        <section>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <h1 className="page-title">Notifications</h1>
                {items.length > 0 && (
                    <button type="button" className="btn ghost" onClick={clear}>Clear</button>
                )}
            </div>
            {items.length === 0 ? (
                <p className="empty">No notifications yet. New events will appear here in real time.</p>
            ) : (
                <table className="table">
                    <thead>
                        <tr>
                            <th>Time</th>
                            <th>Type</th>
                            <th>Message</th>
                        </tr>
                    </thead>
                    <tbody>
                        {items.map((n) => (
                            <tr key={n.id}>
                                <td>{formatDateTime(n.createdAt)}</td>
                                <td><code>{n.type}</code></td>
                                <td>{n.message}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </section>
    );
}
