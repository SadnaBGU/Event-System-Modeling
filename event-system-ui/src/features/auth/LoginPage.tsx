import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { authApi } from '../../api/endpoints/auth';
import { inferRoles, useAuthStore } from '../../auth/authStore';
import { extractApiMessage } from '../../api/client';
import './AuthPage.css';

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const mutation = useMutation({
    mutationFn: async () => {
      const res = await authApi.login({ username, plaintextPassword: password });
      // Seed the session first so subsequent calls carry the Bearer token, then
      // resolve roles using the canonical username from the member resource.
      setSession({ token: res.token, memberId: res.memberId, roles: ['MEMBER'], expiresAt: res.expiresAt });
      const me = await authApi.getMember(res.memberId);
      setSession({
        token: res.token,
        memberId: res.memberId,
        username: me.username,
        roles: inferRoles(res.token, me.username),
        expiresAt: res.expiresAt,
      });
      return res;
    },
    onSuccess: () => {
      toast.success('Signed in successfully.');
      navigate(from, { replace: true });
    },
    onError: (err) => {
      // Global interceptor already toasted. We surface inline too for accessibility.
      // No-op here; inline error rendered below from mutation.error
      void err;
    },
  });

  return (
    <div className="auth-page">
      <form
        className="auth-card"
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
      >
        <h1>Sign in</h1>
        <label>
          Username
          <input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {mutation.isError && (
          <p className="auth-error" role="alert">{extractApiMessage(mutation.error)}</p>
        )}
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="auth-alt">
          No account yet? <Link to="/register">Create one</Link>
        </p>
      </form>
    </div>
  );
}
