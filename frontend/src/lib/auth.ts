import { createContext, useContext } from 'react'
import type { UserProfile } from './types'

export interface AuthState {
  user: UserProfile | null
  /** True until we know whether a refresh cookie restored the session. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (fullName: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  setUser: (u: UserProfile) => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
