import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { PermissionDenied } from '../ui/States';
import type { ProviderRole } from '../../schemas/auth.schema';

interface ProtectedRouteProps {
  /**
   * Optional list of roles allowed to access the child routes.
   * If omitted, any authenticated user may access the route.
   */
  roles?: ProviderRole[];
}

/**
 * Route wrapper that enforces authentication and optional role checks.
 *
 * - Not authenticated → redirects to login (handled by parent App.tsx auth gate)
 * - Authenticated but wrong role → renders <PermissionDenied> (existing component)
 * - Authenticated and correct role → renders <Outlet>
 *
 * This component is a UX convenience only.
 * The real security boundary is enforced server-side via Spring Security @PreAuthorize.
 */
export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { isAuthenticated, currentUser } = useAuth();

  if (!isAuthenticated) {
    // Should not normally reach here as App.tsx gates before AppShell,
    // but acts as a safety net for direct URL navigation.
    return <Navigate to="/" replace />;
  }

  if (roles && roles.length > 0 && currentUser) {
    const hasRole = roles.includes(currentUser.role);
    if (!hasRole) {
      return <PermissionDenied detail="You do not have the required role to access this page." />;
    }
  }

  return <Outlet />;
}
