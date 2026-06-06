import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore, type Role } from './authStore';

interface Props {
  children: React.ReactNode;
  roles?: Role[]; // if provided, user must have at least one of these
}

export function RequireAuth({ children, roles }: Props) {
  const session = useAuthStore((s) => s.session);
  const hydrated = useAuthStore((s) => s.hydrated);
  const location = useLocation();

  if (!hydrated) return null; // still bootstrapping

  if (!session) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (roles && !roles.some((r) => session.roles.includes(r))) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
