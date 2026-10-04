import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from '../context/AuthContext';

/**
 * Hook to access the authentication context.
 * Must be used within <AuthContextProvider>.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthContextProvider');
  }
  return ctx;
}
