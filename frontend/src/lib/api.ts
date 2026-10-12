import type { AuthResponse } from './types'

/** Base URL of the API. Empty = same origin (Vite proxy in dev, Vercel rewrite / nginx in prod). */
export const API_URL: string = import.meta.env.VITE_API_URL ?? ''

/** RFC 9457 problem details returned by the backend. */
export class ApiError extends Error {
  readonly status: number
  readonly errors?: Record<string, string>

  constructor(status: number, message: string, errors?: Record<string, string>) {
    super(message)
    this.status = status
    this.errors = errors
  }
}

// ---- access token management (kept in memory only; the refresh token is an httpOnly cookie) ----

let accessToken: string | null = null
let expiresAt = 0
let refreshing: Promise<AuthResponse | null> | null = null
let onSessionChange: ((auth: AuthResponse | null) => void) | null = null

export function setSessionListener(listener: (auth: AuthResponse | null) => void) {
  onSessionChange = listener
}

export function setSession(auth: AuthResponse | null) {
  accessToken = auth?.accessToken ?? null
  expiresAt = auth ? Date.now() + auth.expiresIn * 1000 : 0
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request
 * (the server rotates the cookie, so parallel refreshes would otherwise race).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshing ??= fetch(`${API_URL}/api/auth/refresh`, { method: 'POST', credentials: 'include' })
    .then(async (res) => (res.ok ? ((await res.json()) as AuthResponse) : null))
    .catch(() => null)
    .then((auth) => {
      setSession(auth)
      onSessionChange?.(auth)
      return auth
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

/** A valid access token, refreshing it shortly before it expires. */
export async function getAccessToken(): Promise<string | null> {
  if (accessToken && Date.now() < expiresAt - 30_000) return accessToken
  const auth = await refreshSession()
  return auth?.accessToken ?? null
}

// ---- fetch wrapper ----

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

async function request<T>(method: Method, path: string, body?: unknown, retry = true): Promise<T> {
  const token = path.startsWith('/api/auth/') ? null : await getAccessToken()
  const res = await fetch(`${API_URL}${path}`, {
    method,
    credentials: 'include',
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    // Token may have been revoked/expired server-side: refresh once and retry.
    const auth = await refreshSession()
    if (auth) return request<T>(method, path, body, false)
  }
  if (!res.ok) {
    let message = res.statusText || 'Request failed'
    let errors: Record<string, string> | undefined
    try {
      const problem = await res.json()
      message = problem.detail ?? problem.title ?? message
      errors = problem.errors
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, message, errors)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  delete: <T = void>(path: string) => request<T>('DELETE', path),
}

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong')

/** Builds a query string, skipping empty values. */
export function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const s = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') s.set(k, String(v))
  }
  const str = s.toString()
  return str ? `?${str}` : ''
}
