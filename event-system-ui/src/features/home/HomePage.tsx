import { Link } from 'react-router-dom';
import { useAuthStore } from '../../auth/authStore';
import '../../components/common.css';
import './HomePage.css';

export function HomePage() {
  const session = useAuthStore((s) => s.session);

  if (!session) {
    return (
      <section className="hero">
        <span className="hero-eyebrow">Live events, made simple</span>
        <h1 className="hero-title">
          Discover events and book tickets <span className="grad">in seconds</span>
        </h1>
        <p className="hero-sub">
          Browse the catalog as a guest, or sign in to buy tickets, join lotteries, and
          manage your own production company.
        </p>
        <div className="hero-actions">
          <Link to="/events" className="btn">Browse events</Link>
          <Link to="/login" className="btn ghost">Sign in</Link>
          <Link to="/register" className="btn ghost">Create account</Link>
        </div>
      </section>
    );
  }

  const isAdmin = session.roles.includes('ADMIN');

  return (
    <section>
      <span className="hero-eyebrow">Welcome back</span>
      <h1 className="page-title" style={{ marginTop: '0.35rem' }}>
        {session.username ?? session.memberId}
      </h1>
      <div className="meta-row">
        <span className="meta">Signed in as</span>
        {session.roles.map((r) => (
          <span key={r} className="pill ACTIVE">{r}</span>
        ))}
      </div>

      <div className="card-grid" style={{ marginTop: '1.75rem' }}>
        <Link to="/events" className="card home-tile">
          <h3>🎟️ Browse the catalog</h3>
          <p className="meta">Find upcoming events and grab your tickets.</p>
        </Link>
        <Link to="/history" className="card home-tile">
          <h3>🧾 My receipts</h3>
          <p className="meta">Review past purchases and download details.</p>
        </Link>
        <Link to="/companies" className="card home-tile">
          <h3>🏢 Companies</h3>
          <p className="meta">Manage the companies you own or help run.</p>
        </Link>
        <Link to="/notifications" className="card home-tile">
          <h3>🔔 Notifications</h3>
          <p className="meta">Catch up on appointments and order updates.</p>
        </Link>
        {isAdmin && (
          <Link to="/admin" className="card home-tile">
            <h3>🛡️ Admin console</h3>
            <p className="meta">Suspensions, bans, companies, and global history.</p>
          </Link>
        )}
      </div>
    </section>
  );
}
