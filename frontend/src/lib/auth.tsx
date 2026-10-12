import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, refreshSession, setSession, setSessionListener } from './api'
import type { AuthResponse, UserProfile } from './types'

interface AuthState {
  user: UserProfile | null
  /** True until we know whether a refresh cookie restored the session. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (fullName: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  setUser: (u: UserProfile) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const queryClient = useQueryClient()

  useEffect(() => {
    // Any refresh (including background ones) keeps React state in sync; a failed one logs out.
    setSessionListener((auth) => setUser(auth?.user ?? null))
    refreshSession().finally(() => setLoading(false))
  }, [])

  const apply = useCallback((auth: AuthResponse) => {
    setSession(auth)
    setUser(auth.user)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      queryClient.clear()
      apply(await api.post<AuthResponse>('/api/auth/login', { email, password }))
    },
    [apply, queryClient],
  )

  const register = useCallback(
    async (fullName: string, email: string, password: string) => {
      queryClient.clear()
      apply(await api.post<AuthResponse>('/api/auth/register', { fullName, email, password }))
    },
    [apply, queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      setSession(null)
      setUser(null)
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo(
    () => ({ user, loading, login, register, logout, setUser }),
    [user, loading, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
