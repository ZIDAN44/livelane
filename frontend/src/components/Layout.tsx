import { useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bell, LayoutGrid, LogOut, Moon, Shield, Sun, User } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { timeAgo } from '../lib/format'
import { useRealtime, useSubscription } from '../lib/realtime'
import type { NotificationView, Page } from '../lib/types'
import { Avatar } from './ui'

export function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 font-bold tracking-tight">
      <img src="/favicon.svg" alt="" className="size-7" />
      <span>LiveLane</span>
    </Link>
  )
}

export function ThemeToggle() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'))
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    try {
      localStorage.setItem('tpm-theme', dark ? 'dark' : 'light')
    } catch {
      /* storage unavailable */
    }
  }, [dark])
  return (
    <button className="btn-ghost p-2" onClick={() => setDark((d) => !d)} aria-label="Toggle dark mode">
      {dark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  )
}

function useClickOutside(onOutside: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const handler = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && onOutside()
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onOutside])
  return ref
}

function NotificationBell() {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const ref = useClickOutside(() => setOpen(false))

  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api.get<{ count: number }>('/api/notifications/unread-count'),
  })
  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api.get<Page<NotificationView>>('/api/notifications?size=15'),
    enabled: open,
  })

  // Live push from the server: toast + refresh the badge.
  useSubscription('/user/queue/notifications', (body) => {
    const n = body as NotificationView
    toast(n.message, {
      action: n.link ? { label: 'Open', onClick: () => navigate(n.link!) } : undefined,
    })
    void queryClient.invalidateQueries({ queryKey: ['notifications'] })
  })

  const markAll = async () => {
    await api.post('/api/notifications/read-all')
    void queryClient.invalidateQueries({ queryKey: ['notifications'] })
  }

  const openNotification = async (n: NotificationView) => {
    setOpen(false)
    if (!n.read) {
      await api.post(`/api/notifications/${n.id}/read`)
      void queryClient.invalidateQueries({ queryKey: ['notifications'] })
    }
    if (n.link) navigate(n.link)
  }

  const count = unread.data?.count ?? 0
  return (
    <div className="relative" ref={ref}>
      <button className="btn-ghost relative p-2" onClick={() => setOpen((o) => !o)} aria-label="Notifications">
        <Bell className="size-4" />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold leading-4 text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div className="card absolute right-0 z-40 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden shadow-xl">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <span className="text-sm font-semibold">Notifications</span>
            {count > 0 && (
              <button className="text-xs font-medium text-brand hover:underline" onClick={markAll}>
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto scroll-thin">
            {list.data?.content.length === 0 && <p className="px-4 py-8 text-center text-sm text-muted">You're all caught up 🎉</p>}
            {list.data?.content.map((n) => (
              <button
                key={n.id}
                onClick={() => openNotification(n)}
                className={clsx(
                  'flex w-full gap-3 border-b border-line px-4 py-3 text-left text-sm last:border-0 hover:bg-surface-2',
                  !n.read && 'bg-brand-soft/60',
                )}
              >
                <span className={clsx('mt-1.5 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-brand')} />
                <span>
                  <span className="block">{n.message}</span>
                  <span className="text-xs text-muted">{timeAgo(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function UserMenu() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useClickOutside(() => setOpen(false))
  const navigate = useNavigate()
  if (!user) return null
  return (
    <div className="relative" ref={ref}>
      <button className="btn-ghost gap-2 px-1.5 py-1" onClick={() => setOpen((o) => !o)} aria-label="Account menu">
        <Avatar user={user} size={28} />
      </button>
      {open && (
        <div className="card absolute right-0 z-40 mt-2 w-56 overflow-hidden py-1 shadow-xl">
          <div className="border-b border-line px-4 py-2.5">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="truncate text-xs text-muted">{user.email}</p>
          </div>
          <Link to="/profile" className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-surface-2" onClick={() => setOpen(false)}>
            <User className="size-4" /> Profile
          </Link>
          <button
            className="flex w-full items-center gap-2 px-4 py-2 text-sm text-danger hover:bg-surface-2"
            onClick={async () => {
              await logout()
              navigate('/login')
            }}
          >
            <LogOut className="size-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  )
}

function LiveIndicator() {
  const { connected } = useRealtime()
  return (
    <span
      className="hidden items-center gap-1.5 text-xs text-muted sm:flex"
      title={connected ? 'Real-time updates connected' : 'Reconnecting...'}
    >
      <span className={clsx('size-2 rounded-full', connected ? 'bg-success animate-pulse' : 'bg-warn')} />
      {connected ? 'Live' : 'Offline'}
    </span>
  )
}

export default function Layout() {
  const { user } = useAuth()
  const navClass = ({ isActive }: { isActive: boolean }) =>
    clsx('btn px-2.5 py-1.5', isActive ? 'bg-surface-2 text-fg' : 'text-muted hover:text-fg')
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-30 border-b border-line bg-surface/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-[1600px] items-center gap-4 px-4">
          <Logo />
          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={navClass}>
              <LayoutGrid className="size-4" /> <span className="hidden sm:inline">Projects</span>
            </NavLink>
            {user?.role === 'ADMIN' && (
              <NavLink to="/admin" className={navClass}>
                <Shield className="size-4" /> <span className="hidden sm:inline">Admin</span>
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <LiveIndicator />
            <ThemeToggle />
            <NotificationBell />
            <UserMenu />
          </div>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}
