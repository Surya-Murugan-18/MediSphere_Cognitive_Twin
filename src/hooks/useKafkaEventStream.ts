/**
 * useKafkaEventStream — subscribes to /topic/kafka.events
 *
 * Per tasks.md F5.2:
 *   Maintains a rolling list of the last 20 Kafka events in state.
 *   Used by Monitoring.tsx and Dashboard.tsx for the "Latest events" panel.
 */

import { useEffect, useRef, useState } from 'react';
import { useWebSocket } from './useWebSocket';
import { KafkaEventEntrySchema, type KafkaEventEntry } from '../schemas/monitoring.schema';

const MAX_EVENTS = 20;

export function useKafkaEventStream(): {
  events: KafkaEventEntry[];
  isConnected: boolean;
} {
  const { subscribe, unsubscribe, isConnected } = useWebSocket();
  const [events, setEvents] = useState<KafkaEventEntry[]>([]);
  const subIdRef = useRef<string | null>(null);

  useEffect(() => {
    const id = subscribe('/topic/kafka.events', (body) => {
      try {
        // The backend sends { topic, key, text, timestamp }
        // Coerce into KafkaEventEntry shape (add a synthetic id + time field)
        const raw = body as Record<string, unknown>;
        const entry: KafkaEventEntry = KafkaEventEntrySchema.parse({
          id:        `k-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          time:      raw.timestamp
            ? new Date(raw.timestamp as string).toLocaleTimeString('en-GB', { hour12: false })
            : new Date().toLocaleTimeString('en-GB', { hour12: false }),
          topic:     raw.topic ?? 'unknown',
          text:      raw.text  ?? '',
          timestamp: raw.timestamp ?? null,
        });

        setEvents((prev) => {
          const next = [entry, ...prev];
          return next.slice(0, MAX_EVENTS);
        });
      } catch (e) {
        console.warn('[useKafkaEventStream] invalid payload', e);
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

  return { events, isConnected };
}
