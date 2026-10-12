import clsx from 'clsx'
import { X } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { PRIORITY_STYLE, dueState, formatDue, titleCase } from '../lib/format'
import type { Priority, UserSummary } from '../lib/types'

export function Avatar({ user, size = 24, ring = false }: { user: UserSummary | null; size?: number; ring?: boolean }) {
  if (!user) {
    return (
      <span
        className="inline-flex shrink-0 items-center justify-center rounded-full border border-dashed border-line text-muted"
        style={{ width: size, height: size, fontSize: size * 0.42 }}
        title="Unassigned"
      >
        ?
      </span>
    )
  }
  const initials = user.fullName
    .split(/\s+/)
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
  return (
    <span
      className={clsx(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold text-white',
        ring && 'ring-2 ring-surface',
      )}
      style={{ width: size, height: size, background: user.avatarColor, fontSize: size * 0.4 }}
      title={user.fullName}
    >
      {initials}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className={clsx('rounded px-1.5 py-0.5 text-[11px] font-semibold tracking-wide', PRIORITY_STYLE[priority])}>
      {titleCase(priority)}
    </span>
  )
}

export function DueBadge({ dueDate, completed }: { dueDate: string | null; completed: boolean }) {
  const state = dueState(dueDate, completed)
  if (!dueDate || !state) return null
  const style = {
    overdue: 'bg-red-500/15 text-red-700 dark:text-red-300',
    today: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
    soon: 'bg-amber-500/10 text-amber-700 dark:text-amber-300',
    later: 'bg-surface-2 text-muted',
    done: 'bg-green-500/10 text-green-700 dark:text-green-300 line-through',
  }[state]
  const label = state === 'overdue' ? `Overdue · ${formatDue(dueDate)}` : state === 'today' ? 'Due today' : formatDue(dueDate)
  return <span className={clsx('rounded px-1.5 py-0.5 text-[11px] font-medium', style)}>{label}</span>
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={clsx('inline-block size-4 animate-spin rounded-full border-2 border-current border-r-transparent', className)}
      role="status"
      aria-label="Loading"
    />
  )
}

export function PageSpinner() {
  return (
    <div className="flex h-64 items-center justify-center text-muted">
      <Spinner className="size-6" />
    </div>
  )
}

export function Modal({
  open,
  onClose,
  title,
  children,
  wide = false,
}: {
  open: boolean
  onClose: () => void
  title?: ReactNode
  children: ReactNode
  wide?: boolean
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, onClose])

  if (!open) return null
  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-4 backdrop-blur-[2px] sm:pt-16"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        className={clsx('card w-full shadow-2xl', wide ? 'max-w-4xl' : 'max-w-lg')}
      >
        {title !== undefined && (
          <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
            <h2 className="text-base font-semibold">{title}</h2>
            <button className="btn-ghost -mr-2 p-1.5" onClick={onClose} aria-label="Close">
              <X className="size-4" />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-line px-6 py-12 text-center">
      <div className="mb-3 text-muted">{icon}</div>
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-sm text-muted">{children}</div>}
    </div>
  )
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <div className="flex items-center justify-end gap-2 text-sm">
      <button className="btn-outline px-2.5 py-1" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="text-muted">
        Page {page + 1} of {totalPages}
      </span>
      <button className="btn-outline px-2.5 py-1" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </div>
  )
}

export function FieldError({ message }: { message?: string }) {
  return message ? <p className="mt-1 text-xs text-danger">{message}</p> : null
}
