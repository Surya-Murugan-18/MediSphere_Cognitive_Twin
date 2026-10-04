/**
 * useMonitoringStream — subscribes to /topic/monitoring.vitals
 *
 * Per tasks.md F5.2:
 *   Returns a Map<patientId, VitalsSnapshot> updated in real-time
 *   as the backend pushes snapshots from the Kafka vitals.raw stream.
 *
 * Used by Monitoring.tsx to update the live patient table without polling.
 */

import { useEffect, useRef, useState } from 'react';
import { useWebSocket } from './useWebSocket';
import { VitalsSnapshotSchema, type VitalsSnapshot } from '../schemas/vitals.schema';

export function useMonitoringStream(): {
  liveVitals: Map<string, VitalsSnapshot>;
  isConnected: boolean;
} {
  const { subscribe, unsubscribe, isConnected } = useWebSocket();
  const [liveVitals, setLiveVitals] = useState<Map<string, VitalsSnapshot>>(new Map());
  const subIdRef = useRef<string | null>(null);

  useEffect(() => {
    const id = subscribe('/topic/monitoring.vitals', (body) => {
      try {
        const snapshot = VitalsSnapshotSchema.parse(body);
        if (!snapshot.patientId) return;

        setLiveVitals((prev) => {
          const next = new Map(prev);
          next.set(snapshot.patientId, snapshot);
          return next;
        });
      } catch (e) {
        console.warn('[useMonitoringStream] invalid payload', e);
      }
    });

    subIdRef.current = id;

    return () => {
      if (subIdRef.current) {
        unsubscribe(subIdRef.current);
        subIdRef.current = null;
      }
    };
  }, [subscribe, unsubscribe]);

  return { liveVitals, isConnected };
}
