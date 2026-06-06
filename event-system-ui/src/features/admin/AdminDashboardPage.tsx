import { Link } from 'react-router-dom';
import '../../components/common.css';

export function AdminDashboardPage() {
  return (
    <section>
      <h1 className="page-title">Admin</h1>
      <p className="meta">System administration tools.</p>

      <div className="card-grid" style={{ marginTop: '1rem' }}>
        <article className="card">
          <h3>Member suspensions</h3>
          <p className="meta">Suspend, unsuspend, and view current suspensions.</p>
          <div className="actions">
            <Link to="/admin/suspensions" className="btn">Open</Link>
          </div>
        </article>
        <article className="card">
          <h3>Ban a member</h3>
          <p className="meta">Permanently remove a member from the platform.</p>
          <div className="actions">
            <Link to="/admin/members" className="btn">Open</Link>
          </div>
        </article>
        <article className="card">
          <h3>Close a company</h3>
          <p className="meta">Shut down a production company.</p>
          <div className="actions">
            <Link to="/admin/companies" className="btn">Open</Link>
          </div>
        </article>
        <article className="card">
          <h3>Global purchase history</h3>
          <p className="meta">All purchases across the platform.</p>
          <div className="actions">
            <Link to="/admin/history" className="btn">Open</Link>
          </div>
        </article>
      </div>
    </section>
  );
}
