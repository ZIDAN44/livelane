import type { Priority, ProjectRole } from './types'

const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

export function timeAgo(iso: string): string {
  const diff = (new Date(iso).getTime() - Date.now()) / 1000
  const abs = Math.abs(diff)
  if (abs < 45) return 'just now'
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour')
  if (abs < 86400 * 30) return rtf.format(Math.round(diff / 86400), 'day')
  return new Date(iso).toLocaleDateString()
}

/** Today's date as YYYY-MM-DD in local time. */
export function todayIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export type DueState = 'overdue' | 'today' | 'soon' | 'later' | 'done'

export function dueState(dueDate: string | null, completed: boolean): DueState | null {
  if (!dueDate) return null
  if (completed) return 'done'
  const today = todayIso()
  if (dueDate < today) return 'overdue'
  if (dueDate === today) return 'today'
  const days = (new Date(dueDate).getTime() - new Date(today).getTime()) / 86_400_000
  return days <= 2 ? 'soon' : 'later'
}

export function formatDue(dueDate: string): string {
  const [y, m, d] = dueDate.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export const PRIORITY_STYLE: Record<Priority, string> = {
  LOW: 'bg-slate-500/10 text-slate-600 dark:text-slate-300',
  MEDIUM: 'bg-sky-500/10 text-sky-700 dark:text-sky-300',
  HIGH: 'bg-orange-500/15 text-orange-700 dark:text-orange-300',
  URGENT: 'bg-red-500/15 text-red-700 dark:text-red-300',
}

export const ROLE_LABEL: Record<ProjectRole, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  MEMBER: 'Member',
  VIEWER: 'Viewer',
}

export const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()
