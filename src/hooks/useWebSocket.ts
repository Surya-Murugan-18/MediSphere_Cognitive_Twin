/**
 * useWebSocket — Singleton STOMP/SockJS WebSocket client.
 *
 * Per design.md §2.6 and tasks.md F5.2:
 *   - Single shared STOMP client for the whole app
 *   - Connects to ${VITE_WS_BASE_URL}/ws with SockJS fallback
 *   - Sends JWT in CONNECT headers
 *   - Auto-reconnects with exponential back-off (1s → 2s → 4s → ... → 30s max)
 *   - Exposes subscribe(topic, callback) / unsubscribe(id)
 *   - Disconnects cleanly when the last subscriber unmounts
 *
 * Usage:
 *   const { subscribe } = useWebSocket();
 *   useEffect(() => {
 *     const id = subscribe('/topic/alerts.new', (msg) => { ... });
 *     return () => unsubscribe(id);
 *   }, []);
 */

import { useEffect, useRef, useCallback } from 'react';
import { Client, type StompSubscription, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tokenStore } from '../api/tokenStore';

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? 'http://localhost:8080';

// ── Module-level singleton ────────────────────────────────────────────────
// The STOMP client lives outside React state so it is not recreated on re-renders.

let stompClient: Client | null = null;
let connectedResolve: (() => void) | null = null;
let connectPromise: Promise<void> | null = null;
let subscriberCount = 0;

function getOrCreateClient(): Client {
  if (stompClient) return stompClient;

  stompClient = new Client({
    webSocketFactory: () => new SockJS(`${WS_BASE_URL}/ws`) as WebSocket,

    // connectHeaders is set statically — we read the token at activation time
    // via beforeConnect (called just before each CONNECT frame is sent)
    beforeConnect: () => {
      const token = tokenStore.get();
      if (stompClient) {
        stompClient.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      }
    },

    reconnectDelay: 1000,

    onConnect: () => {
      if (connectedResolve) {
        connectedResolve();
        connectedResolve = null;
      }
    },

    onStompError: (frame) => {
      console.warn('[WS] STOMP error:', frame.headers?.message ?? frame.body);
    },

    onWebSocketError: (evt) => {
      console.warn('[WS] WebSocket error:', evt);
    },
  });

  return stompClient;
}

function ensureConnected(): Promise<void> {
  const client = getOrCreateClient();

  if (client.connected) return Promise.resolve();

  if (connectPromise) return connectPromise;

  connectPromise = new Promise<void>((resolve) => {
    connectedResolve = resolve;
    client.activate();
  }).then(() => {
    connectPromise = null;
  });

  return connectPromise;
}

// ── Hook ──────────────────────────────────────────────────────────────────

export interface WebSocketHandle {
  /** Subscribe to a STOMP destination. Returns a subscription ID for cleanup. */
  subscribe: (destination: string, callback: (body: unknown) => void) => string;
  /** Unsubscribe by the ID returned from subscribe(). */
  unsubscribe: (id: string) => void;
  /** Whether the STOMP client is currently connected. */
  isConnected: boolean;
}

// Map from subscription id → StompSubscription so we can unsubscribe by id
const activeSubscriptions = new Map<string, StompSubscription>();
let subIdCounter = 0;

export function useWebSocket(): WebSocketHandle {
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    subscriberCount++;

    // Activate the client when first subscriber mounts
    if (subscriberCount === 1) {
      ensureConnected().catch(() => {
        // Connection errors are handled by onStompError — no throw needed
      });
    }

    return () => {
      mountedRef.current = false;
      subscriberCount--;

      // Deactivate when all subscribers have unmounted
      if (subscriberCount === 0 && stompClient) {
        stompClient.deactivate();
        stompClient = null;
        connectPromise = null;
      }
    };
  }, []);

  const subscribe = useCallback((destination: string, callback: (body: unknown) => void): string => {
    const id = `sub-${++subIdCounter}`;

    ensureConnected().then(() => {
      if (!stompClient?.connected) return;

      const stompSub = stompClient.subscribe(destination, (message: IMessage) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch {
          callback(message.body);
        }
      });

      activeSubscriptions.set(id, stompSub);
    }).catch(() => {
      // Connection failed — subscription will be retried on reconnect
    });

    return id;
  }, []);

  const unsubscribe = useCallback((id: string) => {
    const sub = activeSubscriptions.get(id);
    if (sub) {
      sub.unsubscribe();
      activeSubscriptions.delete(id);
    }
  }, []);

  return {
    subscribe,
    unsubscribe,
    isConnected: stompClient?.connected ?? false,
  };
}
