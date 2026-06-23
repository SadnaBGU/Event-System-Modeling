import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../auth/authStore';
import { useNotificationStore } from '../notifications/notificationStore';
import './Layout.css';

export function Layout() {
  const session = useAuthStore((s) => s.session);
  const clear = useAuthStore((s) => s.clear);
  const unread = useNotificationStore((s) => s.unread);
  const markAllRead = useNotificationStore((s) => s.markAllRead);
  const navigate = useNavigate();

  const isAdmin = session?.roles.includes('ADMIN') ?? false;

  function handleLogout() {
    clear();
    navigate('/login', { replace: true });
  }

  return (
    <div className="layout">
      <header className="layout-header">
        <Link to="/" className="brand">EventSystem</Link>
        <nav className="layout-nav">
          {/* Catalog is open to everyone, including guests (browse without signing up). */}
          <NavLink to="/events">Catalog</NavLink>
          {session && <NavLink to="/history">My receipts</NavLink>}
          {session && <NavLink to="/companies">Companies</NavLink>}
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <div className="layout-right">
          {session ? (
            <>
              <button
                type="button"
                className="bell"
                onClick={() => {
                  markAllRead();
                  navigate('/notifications');
                }}
                aria-label="Notifications"
              >
                🔔{unread > 0 && <span className="badge">{unread}</span>}
              </button>
              <Link to="/profile" className="user" title="My profile">{session.username ?? session.memberId}</Link>
              <button type="button" onClick={handleLogout}>Sign out</button>
            </>
          ) : (
            <Link to="/login">Sign in</Link>
          )}
        </div>
      </header>
      <main className="layout-main">
        <Outlet />
      </main>
    </div>
  );
}
