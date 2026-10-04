import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * TanStack Query client with MediSphere-appropriate defaults.
 * - staleTime 30s: clinical data refreshes every 30s max on window focus
 * - gcTime 5m: keeps data in cache for 5 minutes after components unmount
 * - retry 2: retry failed requests twice before showing error state
 * - throwOnError false: errors handled per-query via isError/error state
 * - refetchOnWindowFocus true: keeps data fresh when user switches tabs
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,          // 30 seconds
      gcTime: 5 * 60 * 1000,         // 5 minutes
      retry: 2,
      refetchOnWindowFocus: true,
      throwOnError: false,
    },
    mutations: {
      throwOnError: false,
    },
  },
});

export function QueryProvider({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
