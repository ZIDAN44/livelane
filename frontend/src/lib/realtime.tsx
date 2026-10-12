import { Client, type StompSubscription } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { API_URL, getAccessToken } from './api'
import { useAuth } from './auth'

/** WebSocket URL: explicit VITE_WS_URL, else derived from the API URL or the current origin. */
function wsUrl(): string {
  const explicit = import.meta.env.VITE_WS_URL as string | undefined
  if (explicit) return explicit
  const base = API_URL || window.location.origin
  return base.replace(/^http/, 'ws') + '/ws'
}

type Handler = (body: unknown) => void

interface Realtime {
  connected: boolean
  /** Subscribes (and transparently re-subscribes after reconnects). Returns an unsubscribe fn. */
  subscribe: (destination: string, handler: Handler) => () => void
}

const RealtimeContext = createContext<Realtime | null>(null)

/**
 * One STOMP connection per signed-in user. The JWT goes in the CONNECT frame (browsers can't set
 * headers on the WebSocket handshake); a fresh token is fetched before every (re)connect.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const subs = useRef(new Map<number, { destination: string; handler: Handler; stomp?: StompSubscription }>())
  const nextId = useRef(1)

  useEffect(() => {
    if (!user) return
    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async (c) => {
        const token = await getAccessToken()
        c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
      },
      onConnect: () => {
        setConnected(true)
        for (const sub of subs.current.values()) {
          sub.stomp = client.subscribe(sub.destination, (m) => sub.handler(JSON.parse(m.body)))
        }
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => console.warn('STOMP error', frame.headers.message),
    })
    clientRef.current = client
    client.activate()
    return () => {
      clientRef.current = null
      setConnected(false)
      void client.deactivate()
    }
  }, [user])

  const subscribe = useCallback((destination: string, handler: Handler) => {
    const id = nextId.current++
    const entry: { destination: string; handler: Handler; stomp?: StompSubscription } = { destination, handler }
    subs.current.set(id, entry)
    const client = clientRef.current
    if (client?.connected) entry.stomp = client.subscribe(destination, (m) => handler(JSON.parse(m.body)))
    return () => {
      subs.current.delete(id)
      if (clientRef.current?.connected) entry.stomp?.unsubscribe()
    }
  }, [])

  const value = useMemo(() => ({ connected, subscribe }), [connected, subscribe])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime() {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime must be used inside <RealtimeProvider>')
  return ctx
}

/** Subscribe to a destination for the lifetime of the component. */
export function useSubscription(destination: string | null, handler: Handler) {
  const { subscribe } = useRealtime()
  const handlerRef = useRef(handler)
  useEffect(() => {
    handlerRef.current = handler
  })
  useEffect(() => {
    if (!destination) return
    return subscribe(destination, (body) => handlerRef.current(body))
  }, [destination, subscribe])
}
