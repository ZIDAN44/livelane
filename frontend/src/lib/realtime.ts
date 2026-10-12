import { createContext, useContext, useEffect, useRef } from 'react'

export type Handler = (body: unknown) => void

export interface Realtime {
  connected: boolean
  /** Subscribes (and transparently re-subscribes after reconnects). Returns an unsubscribe fn. */
  subscribe: (destination: string, handler: Handler) => () => void
}

export const RealtimeContext = createContext<Realtime | null>(null)

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
