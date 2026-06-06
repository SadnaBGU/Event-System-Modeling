import { Link } from 'react-router-dom';
import { useAuthStore } from '../../auth/authStore';

export function HomePage() {
  const session = useAuthStore((s) => s.session);

  if (!session) {
    return (
      <section>
        <h1>EventSystem</h1>
        <p>Sign in to browse events, buy tickets, and manage your account.</p>
        <p><Link to="/login">Sign in</Link> · <Link to="/register">Create account</Link></p>
      </section>
    );
  }

  return (
    <section>
      <h1>Welcome back</h1>
      <p>Member ID: <code>{session.memberId}</code></p>
      <p>Roles: {session.roles.join(', ')}</p>
      <p>Session expires: {new Date(session.expiresAt).toLocaleString()}</p>
      <ul>
        <li><Link to="/events">Browse the catalog</Link></li>
        <li><Link to="/history">View my receipts</Link></li>
      </ul>
    </section>
  );
}
