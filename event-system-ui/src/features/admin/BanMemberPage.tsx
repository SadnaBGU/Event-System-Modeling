import { Link } from 'react-router-dom';
import '../../components/common.css';

export function BanMemberPage() {
  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Ban a member</h1>
      <p className="empty">
        The backend does not currently expose an admin ban endpoint. Use
        <Link to="/admin/suspensions" style={{ marginLeft: '0.25rem' }}>suspensions</Link> instead
        (a permanent suspension achieves the same effect).
      </p>
    </section>
  );
}
