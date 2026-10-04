/**
 * useVitalsStream — subscribes to /topic/vitals.{patientId}
 *
 * Per design.md §2.6 and tasks.md F5.7:
 *   - Returns the latest VitalsSnapshot pushed from the backend
 *   - Merges live data with the TanStack Query cache so the
 *     existing REST-loaded snapshot is immediately replaced
 *     when a WS update arrives
 *
 * Phase 5 activation: replaces the Phase 3 no-op placeholder.
 */

import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useWebSocket } from './useWebSocket';
import { VitalsSnapshotSchema, type VitalsSnapshot } from '../schemas/vitals.schema';

export function useVitalsStream(patientId: string | undefined): {
  isConnected: boolean;
  lastUpdated: string | null;
  liveSnapshot: VitalsSnapshot | null;
} {
  const { subscribe, unsubscribe, isConnected } = useWebSocket();
  const queryClient = useQueryClient();
  const [liveSnapshot, setLiveSnapshot] = useState<VitalsSnapshot | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);
  const [streamConnected, setStreamConnected] = useState(false);
  const subIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!patientId) {
      setStreamConnected(false);
      return;
    }

    const destination = `/topic/vitals.${patientId}`;
    const id = subscribe(destination, (body) => {
      try {
        const snapshot = VitalsSnapshotSchema.parse(body);
        setLiveSnapshot(snapshot);
        setLastUpdated(new Date().toISOString());
        setStreamConnected(true);

        // Merge into TanStack Query cache so REST-based components
        // get the live value without a refetch
        queryClient.setQueryData(['vitals-current', patientId], snapshot);
      } catch (e) {
        console.warn('[useVitalsStream] invalid payload', e);
      }
    });

    subIdRef.current = id;

    return () => {
      if (subIdRef.current) {
        unsubscribe(subIdRef.current);
        subIdRef.current = null;
      }
      setStreamConnected(false);
    };
  }, [patientId, subscribe, unsubscribe, queryClient]);

  return {
    isConnected: isConnected || streamConnected,
    lastUpdated,
    liveSnapshot,
  };
}
