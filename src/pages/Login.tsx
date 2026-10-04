import { useState } from 'react';
import { KeyRoundIcon, LockIcon, ShieldCheckIcon, UserCogIcon } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Field, TextInput } from '../components/ui/Field';
import { useAuth } from '../hooks/useAuth';

/**
 * Login page — UI is identical to the original design.
 * The only change is replacing the setTimeout mock with a real API call
 * via AuthContext.login(). All layout, colors, typography, and structure
 * are preserved exactly.
 *
 * NOTE: onSignIn prop removed — auth state is now managed by AuthContext.
 * App.tsx renders this page when !isAuthenticated; on successful login
 * AuthContext sets isAuthenticated=true and App.tsx switches to AppShell.
 */
export function Login() {
  const { login, isLoading, authError } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<{ email?: string; password?: string; general?: string }>({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();

    // Client-side validation
    const nextErrors: { email?: string; password?: string; general?: string } = {};
    if (!email.trim()) nextErrors.email = 'Provider ID or email is required.';
    if (!password.trim()) nextErrors.password = 'Password is required.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    setErrors({});

    try {
      await login(email, password);
      // On success: AuthContext sets isAuthenticated=true → App.tsx renders AppShell
      // No navigation needed here
    } catch (error: unknown) {
      // AuthContext sets authError, but we also set local errors for field display
      const message = extractLoginErrorMessage(error);
      setErrors({ general: message });
    } finally {
      setSubmitting(false);
    }
  };

  const isDisabled = submitting || isLoading;

  return (
    <div className="flex min-h-full w-full flex-col bg-slate-50">
      <div className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          {/* Logo + title — unchanged */}
          <div className="mb-7 flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-navy-900 text-lg font-bold text-white">M</span>
            <div>
              <p className="text-lg font-semibold leading-tight text-slate-900">MediSphere Cognitive Twin</p>
              <p className="text-xs leading-tight text-slate-500">AI Health Prediction Platform</p>
            </div>
          </div>

          <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-white p-6 shadow-card" noValidate>
            <h1 className="text-base font-semibold text-slate-900">Welcome back</h1>
            <p className="mt-0.5 text-xs text-slate-500">Sign in with your clinical credentials to continue.</p>

            {/* General auth error — shown above the fields */}
           {(errors.general || authError) && (
  <div className="mt-4 rounded-md bg-critical-50 px-3 py-2 text-xs text-critical-600">
    {errors.general || authError}
  </div>
)}

            <div className="mt-5 space-y-4">
              <Field label="Provider ID / Email" htmlFor="email" required error={errors.email}>
                <TextInput
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  invalid={Boolean(errors.email)}
                  autoComplete="username"
                  placeholder="you@hospital.org"
                />
              </Field>

              <Field label="Password" htmlFor="password" required error={errors.password}>
                <TextInput
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  invalid={Boolean(errors.password)}
                  autoComplete="current-password"
                />
              </Field>

              <div className="flex items-center justify-between">
                <label className="flex items-center gap-2 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    checked={remember}
                    onChange={(e) => setRemember(e.target.checked)}
                    className="h-3.5 w-3.5 rounded border-slate-300 text-brand-500 focus:ring-brand-500"
                  />
                  Remember me
                </label>
                <button type="button" className="text-xs font-medium text-brand-600 hover:text-brand-700">
                  Forgot password?
                </button>
              </div>

              <Button type="submit" variant="primary" size="lg" className="w-full" disabled={isDisabled}>
                {submitting ? 'Verifying credentials…' : 'Sign In'}
              </Button>
            </div>
          </form>

          {/* Security info panel — unchanged */}
          <div className="mt-5 rounded-lg border border-slate-200 bg-white px-4 py-3">
            <p className="flex items-center gap-2 text-xs font-semibold text-slate-800">
              <ShieldCheckIcon className="h-4 w-4 text-teal-600" aria-hidden="true" />
              Secure Clinical Environment
            </p>
            <ul className="mt-2 space-y-1.5 text-xs text-slate-500">
              <li className="flex items-center gap-2">
                <LockIcon className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                HIPAA-compliant access
              </li>
              <li className="flex items-center gap-2">
                <UserCogIcon className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                Role-based access control
              </li>
              <li className="flex items-center gap-2">
                <KeyRoundIcon className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                SMART on FHIR authorization · all activity audited
              </li>
            </ul>
          </div>

          <p className="mt-5 text-center text-2xs text-slate-400">
            Demonstration environment — no real patient data is processed.
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Error message helper ───────────────────────────────────────────────────

function extractLoginErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { status?: number } };
    const status = axiosError.response?.status;
    if (status === 401) return 'Invalid credentials. Please check your email and password.';
    if (status && status >= 500) return 'Unable to connect to the server. Please try again.';
  }
  if (error && typeof error === 'object' && 'code' in error) {
    const netError = error as { code?: string };
    if (netError.code === 'ERR_NETWORK') return 'Unable to connect to the server. Please check your network connection.';
  }
  return 'Sign in failed. Please try again.';
}
