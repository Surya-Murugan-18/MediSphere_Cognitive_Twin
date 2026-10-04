/**
 * useAlertStream — subscribes to /topic/alerts.new
 *
 * Per tasks.md F5.2:
 *   On new alert received:
 *     - invalidates ['alerts'] TanStack Query cache
 *     - invalidates ['alert-count'] cache
 *   This triggers the Alerts page and Sidebar badge to refetch
 *   without needing to poll.
 */

import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useWebSocket } from './useWebSocket';

export function useAlertStream(): { isConnected: boolean } {
  const { subscribe, unsubscribe, isConnected } = useWebSocket();
  const queryClient = useQueryClient();
  const subIdRef = useRef<string | null>(null);

  useEffect(() => {
    const id = subscribe('/topic/alerts.new', () => {
      // Invalidate alert list and badge count — triggers refetch in all consumers
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alert-count'] });
    });

    subIdRef.current = id;

    return () => {
      if (subIdRef.current) {
        unsubscribe(subIdRef.current);
        subIdRef.current = null;
      }
    };
  }, [subscribe, unsubscribe, queryClient]);

  return { isConnected };
}
