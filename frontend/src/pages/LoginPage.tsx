import { useQuery } from '@tanstack/react-query'
import { ArrowRight, BookOpen, Code2, Zap } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Logo, ThemeToggle } from '../components/Layout'
import { Avatar, Spinner } from '../components/ui'
import { API_URL, api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { DemoLogin } from '../lib/types'

const REPO_URL = import.meta.env.VITE_REPO_URL as string | undefined

/** Free hosting tiers sleep when idle; tell visitors why the first request is slow. */
function useServerWakeUp() {
  const [slow, setSlow] = useState(false)
  const health = useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      const res = await fetch(`${API_URL}/actuator/health`)
      if (!res.ok) throw new Error('down')
      return true
    },
    retry: 10,
    retryDelay: 3000,
  })
  useEffect(() => {
    const t = setTimeout(() => setSlow(true), 2500)
    return () => clearTimeout(t)
  }, [])
  return { ready: health.isSuccess, waking: slow && !health.isSuccess }
}

export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-800 p-12 text-white lg:flex lg:flex-col">
        <div className="flex items-center gap-2 text-lg font-bold">
          <img src="/favicon.svg" alt="" className="size-8 rounded-lg ring-2 ring-white/30" /> TPM
        </div>
        <div className="my-auto max-w-md">
          <h1 className="text-4xl font-bold leading-tight">Plan, track and ship together — in real time.</h1>
          <p className="mt-4 text-indigo-100">
            Kanban boards, role-based access, comments, deadlines and live updates over WebSockets. Built with Spring
            Boot 4, Java 27, PostgreSQL and React.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-indigo-50">
            {[
              'Drag & drop ordering with fractional indexing',
              'Live board sync & presence via STOMP',
              'JWT + rotating refresh tokens, RBAC per project',
              'Email notifications & deadline reminders',
            ].map((f) => (
              <li key={f} className="flex items-center gap-2">
                <Zap className="size-4 text-amber-300" /> {f}
              </li>
            ))}
          </ul>
        </div>
        <div className="flex gap-4 text-sm text-indigo-100">
          <a href={`${API_URL}/swagger-ui`} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 hover:text-white">
            <BookOpen className="size-4" /> API docs
          </a>
          {REPO_URL && (
            <a href={REPO_URL} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 hover:text-white">
              <Code2 className="size-4" /> Source
            </a>
          )}
        </div>
        <div className="pointer-events-none absolute -right-24 -bottom-24 size-96 rounded-full bg-white/10 blur-3xl" />
      </div>
      <div className="flex flex-col">
        <div className="flex items-center justify-between p-4 lg:justify-end">
          <div className="lg:hidden">
            <Logo />
          </div>
          <ThemeToggle />
        </div>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center px-4 pb-16">{children}</div>
      </div>
    </div>
  )
}

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const { waking } = useServerWakeUp()
  const demo = useQuery({
    queryKey: ['demo-accounts'],
    queryFn: () => api.get<DemoLogin[]>('/api/auth/demo-accounts'),
    retry: 10,
    retryDelay: 3000,
  })

  const from = (location.state as { from?: string } | null)?.from ?? '/'
  if (user) return <Navigate to={from} replace />

  const doLogin = async (e: string, p: string, key: string) => {
    setBusy(key)
    try {
      await login(e, p)
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setBusy(null)
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void doLogin(email, password, 'form')
  }

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold">Welcome back</h2>
      <p className="mt-1 text-sm text-muted">Sign in, or jump straight in with a demo account.</p>

      {waking && (
        <div className="mt-4 flex items-center gap-2 rounded-lg bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
          <Spinner className="size-3" /> Waking up the server (free hosting sleeps when idle) — this can take ~30s.
        </div>
      )}

      {demo.data && demo.data.length > 0 && (
        <div className="mt-6">
          <p className="label">One-click demo login</p>
          <div className="space-y-2">
            {demo.data.map((a) => (
              <button
                key={a.email}
                className="card flex w-full items-center gap-3 px-3 py-2.5 text-left transition hover:border-brand hover:shadow-sm disabled:opacity-60"
                disabled={busy !== null}
                onClick={() => doLogin(a.email, a.password, a.email)}
              >
                <Avatar user={{ id: 0, fullName: a.fullName, email: a.email, avatarColor: a.color }} size={32} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{a.fullName}</span>
                  <span className="block truncate text-xs text-muted">{a.description}</span>
                </span>
                {busy === a.email ? <Spinner className="text-brand" /> : <ArrowRight className="size-4 text-muted" />}
              </button>
            ))}
          </div>
          <div className="my-6 flex items-center gap-3 text-xs text-muted">
            <span className="h-px flex-1 bg-line" /> or sign in with email <span className="h-px flex-1 bg-line" />
          </div>
        </div>
      )}

      <form onSubmit={onSubmit} className="space-y-3">
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" className="input" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" className="input" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn-primary w-full" disabled={busy !== null}>
          {busy === 'form' && <Spinner />} Sign in
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        New here?{' '}
        <Link to="/register" className="font-medium text-brand hover:underline">
          Create an account
        </Link>
      </p>
    </AuthShell>
  )
}
