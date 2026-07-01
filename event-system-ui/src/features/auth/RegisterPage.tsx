import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { authApi } from '../../api/endpoints/auth';
import { extractApiMessage } from '../../api/client';
import './AuthPage.css';

interface FormState {
  username: string;
  plaintextPassword: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}

const empty: FormState = {
  username: '',
  plaintextPassword: '',
  firstName: '',
  lastName: '',
  email: '',
  dateOfBirth: '',
};

export function RegisterPage() {
  const [form, setForm] = useState<FormState>(empty);
  const navigate = useNavigate();

  const mutation = useMutation({
    mutationFn: () => authApi.register(form),
    onSuccess: () => {
      toast.success('Account created successfully. Please sign in.');
      navigate('/login', { replace: true });
    },
  });

  function update<K extends keyof FormState>(key: K) {
    return (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));
  }

  return (
    <div className="auth-page">
      <form
        className="auth-card"
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
      >
        <h1>Create account</h1>
        <label>
          Username
          <input value={form.username} onChange={update('username')} required />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="new-password"
            value={form.plaintextPassword}
            onChange={update('plaintextPassword')}
            required
            minLength={8}
          />
        </label>
        <div className="row">
          <label>
            First name
            <input value={form.firstName} onChange={update('firstName')} required />
          </label>
          <label>
            Last name
            <input value={form.lastName} onChange={update('lastName')} required />
          </label>
        </div>
        <label>
          Email
          <input type="email" value={form.email} onChange={update('email')} required />
        </label>
        <label>
          Date of birth
          <input type="date" value={form.dateOfBirth} onChange={update('dateOfBirth')} required />
        </label>
        {mutation.isError && (
          <p className="auth-error" role="alert">{extractApiMessage(mutation.error)}</p>
        )}
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create account'}
        </button>
        <p className="auth-alt">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
