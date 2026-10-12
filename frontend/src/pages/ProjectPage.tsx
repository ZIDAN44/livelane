import { useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Activity, ArrowDown, ArrowUp, Kanban, List, Plus, Search, Settings, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import Board from '../components/Board'
import NewTaskModal from '../components/NewTaskModal'
import ProjectSettings from '../components/ProjectSettings'
import TaskModal from '../components/TaskModal'
import { Avatar, DueBadge, EmptyState, PageSpinner, Pagination, PriorityBadge } from '../components/ui'
import { ApiError, api, errorMessage, qs } from '../lib/api'
import { useAuth } from '../lib/auth'
import { applyEvent, type BoardFilter } from '../lib/board'
import { PRIORITIES, ROLE_LABEL, timeAgo, titleCase } from '../lib/format'
import { useSubscription } from '../lib/realtime'
import { atLeast, type ActivityView, type BoardEvent, type BoardView, type Page, type TaskSummary, type UserSummary } from '../lib/types'

const TABS = [
  { key: 'board', label: 'Board', icon: Kanban },
  { key: 'list', label: 'List', icon: List },
  { key: 'activity', label: 'Activity', icon: Activity },
  { key: 'settings', label: 'Settings', icon: Settings },
] as const

function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

/** Who else is looking at this board right now (from STOMP presence events). */
function Presence({ projectId }: { projectId: number }) {
  const [viewers, setViewers] = useState<UserSummary[]>([])
  useSubscription(`/topic/projects/${projectId}/presence`, (b) => setViewers(b as UserSummary[]))
  useSubscription(`/app/projects/${projectId}/presence`, (b) => setViewers(b as UserSummary[])) // initial snapshot
  if (viewers.length === 0) return null
  return (
    <div className="flex items-center gap-2" title={`Viewing now: ${viewers.map((v) => v.fullName).join(', ')}`}>
      <div className="flex -space-x-1.5">
        {viewers.slice(0, 5).map((v) => <Avatar key={v.id} user={v} size={26} ring />)}
      </div>
      <span className="hidden text-xs text-muted md:inline">{viewers.length === 1 ? 'viewing' : `${viewers.length} viewing`}</span>
    </div>
  )
}

function BoardToolbar({ board, filter, setFilter, canEdit, onNew }: {
  board: BoardView
  filter: BoardFilter
  setFilter: (f: BoardFilter) => void
  canEdit: boolean
  onNew: () => void
}) {
  const active = filter.q || filter.assigneeId || filter.priority
  return (
    <div className="flex flex-wrap items-center gap-2 px-4 pb-3">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted" />
        <input className="input w-56 pl-8" placeholder="Filter cards…" value={filter.q} onChange={(e) => setFilter({ ...filter, q: e.target.value })} />
      </div>
      <select
        className="input w-auto"
        value={filter.assigneeId ?? ''}
        onChange={(e) => setFilter({ ...filter, assigneeId: e.target.value === '' ? null : e.target.value === 'none' ? 'none' : Number(e.target.value) })}
        aria-label="Filter by assignee"
      >
        <option value="">All assignees</option>
        <option value="none">Unassigned</option>
        {board.members.map((m) => <option key={m.user.id} value={m.user.id}>{m.user.fullName}</option>)}
      </select>
      <select className="input w-auto" value={filter.priority ?? ''} onChange={(e) => setFilter({ ...filter, priority: e.target.value || null })} aria-label="Filter by priority">
        <option value="">All priorities</option>
        {PRIORITIES.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
      </select>
      {active && (
        <>
          <button className="btn-ghost text-muted" onClick={() => setFilter({ q: '', assigneeId: null, priority: null })}>
            <X className="size-4" /> Clear
          </button>
          <span className="text-xs text-muted">Drag & drop is paused while filtering</span>
        </>
      )}
      {canEdit && (
        <button className="btn-primary ml-auto" onClick={onNew}>
          <Plus className="size-4" /> New task
        </button>
      )}
    </div>
  )
}

type SortKey = 'createdAt' | 'dueDate' | 'priority' | 'title' | 'number'

function ListTab({ board, onOpenTask }: { board: BoardView; onOpenTask: (id: number) => void }) {
  const projectId = board.project.id
  const [q, setQ] = useState('')
  const [priority, setPriority] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const [columnId, setColumnId] = useState('')
  const [overdue, setOverdue] = useState(false)
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'createdAt', dir: 'desc' })
  const debouncedQ = useDebounced(q)
  // The page belongs to one particular filter combination; changing any filter starts again at page 0
  // (derived during render instead of resetting state in an effect).
  const filterKey = JSON.stringify([debouncedQ, priority, assigneeId, columnId, overdue, sort])
  const [paging, setPaging] = useState({ filterKey, page: 0 })
  const page = paging.filterKey === filterKey ? paging.page : 0
  const setPage = (p: number) => setPaging({ filterKey, page: p })

  const params = qs({ q: debouncedQ, priority, assigneeId, columnId, overdue: overdue || undefined, page, size: 15, sort: `${sort.key},${sort.dir}` })
  const tasks = useQuery({
    queryKey: ['tasks', projectId, params],
    queryFn: () => api.get<Page<TaskSummary>>(`/api/projects/${projectId}/tasks${params}`),
    placeholderData: (prev) => prev,
  })

  const header = (key: SortKey, label: string, className?: string) => (
    <th className={clsx('px-3 py-2 font-medium', className)}>
      <button
        className="inline-flex items-center gap-1 hover:text-fg"
        onClick={() => setSort((s) => ({ key, dir: s.key === key && s.dir === 'asc' ? 'desc' : 'asc' }))}
      >
        {label}
        {sort.key === key && (sort.dir === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}
      </button>
    </th>
  )

  return (
    <div className="mx-auto max-w-6xl space-y-3 px-4 pb-8">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted" />
          <input className="input w-64 pl-8" placeholder="Search titles (server-side)…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <select className="input w-auto" value={columnId} onChange={(e) => setColumnId(e.target.value)} aria-label="Status">
          <option value="">All statuses</option>
          {board.columns.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="input w-auto" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} aria-label="Assignee">
          <option value="">All assignees</option>
          {board.members.map((m) => <option key={m.user.id} value={m.user.id}>{m.user.fullName}</option>)}
        </select>
        <select className="input w-auto" value={priority} onChange={(e) => setPriority(e.target.value)} aria-label="Priority">
          <option value="">All priorities</option>
          {PRIORITIES.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={overdue} onChange={(e) => setOverdue(e.target.checked)} className="accent-[var(--brand)]" /> Overdue only
        </label>
        <span className="ml-auto text-sm text-muted">{tasks.data?.totalElements ?? 0} tasks</span>
      </div>
      <div className="card overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="border-b border-line text-xs text-muted">
            <tr>
              {header('number', 'Key', 'w-24')}
              {header('title', 'Title')}
              <th className="px-3 py-2 font-medium">Status</th>
              {header('priority', 'Priority')}
              <th className="px-3 py-2 font-medium">Assignee</th>
              {header('dueDate', 'Due')}
              {header('createdAt', 'Created')}
            </tr>
          </thead>
          <tbody className={clsx('divide-y divide-line', tasks.isFetching && 'opacity-60')}>
            {tasks.data?.content.map((t) => (
              <tr key={t.id} className="cursor-pointer hover:bg-surface-2" onClick={() => onOpenTask(t.id)}>
                <td className="px-3 py-2.5 font-mono text-xs text-muted">{t.key}</td>
                <td className="max-w-xs truncate px-3 py-2.5 font-medium">{t.title}</td>
                <td className="px-3 py-2.5 text-muted">{t.columnName}</td>
                <td className="px-3 py-2.5"><PriorityBadge priority={t.priority} /></td>
                <td className="px-3 py-2.5">
                  <span className="flex items-center gap-2"><Avatar user={t.assignee} size={20} /> <span className="truncate">{t.assignee?.fullName ?? '—'}</span></span>
                </td>
                <td className="px-3 py-2.5"><DueBadge dueDate={t.dueDate} completed={t.completedAt !== null} /></td>
                <td className="px-3 py-2.5 text-muted">{timeAgo(t.createdAt)}</td>
              </tr>
            ))}
            {tasks.data?.content.length === 0 && (
              <tr><td colSpan={7} className="px-3 py-10 text-center text-muted">No tasks match these filters.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {tasks.data && <Pagination page={tasks.data.page} totalPages={tasks.data.totalPages} onChange={setPage} />}
    </div>
  )
}

function ActivityTab({ projectId }: { projectId: number }) {
  const [page, setPage] = useState(0)
  const activity = useQuery({
    queryKey: ['activity', projectId, page],
    queryFn: () => api.get<Page<ActivityView>>(`/api/projects/${projectId}/activity?page=${page}&size=25`),
    placeholderData: (prev) => prev,
  })
  if (activity.isLoading) return <PageSpinner />
  return (
    <div className="mx-auto max-w-3xl space-y-3 px-4 pb-8">
      <div className="card divide-y divide-line">
        {activity.data?.content.map((a) => (
          <div key={a.id} className="flex items-start gap-3 px-4 py-3 text-sm">
            <Avatar user={a.actor} size={28} />
            <div className="min-w-0 flex-1">
              <span className="font-medium">{a.actor?.fullName ?? 'Deleted user'}</span> <span className="text-muted">{a.summary}</span>
              <div className="mt-0.5 flex gap-2 text-xs text-muted">
                <span>{timeAgo(a.createdAt)}</span>
                <span className="rounded bg-surface-2 px-1.5 font-mono text-[10px]">{a.action}</span>
              </div>
            </div>
          </div>
        ))}
        {activity.data?.content.length === 0 && <p className="p-6 text-center text-sm text-muted">No activity yet.</p>}
      </div>
      {activity.data && <Pagination page={activity.data.page} totalPages={activity.data.totalPages} onChange={setPage} />}
    </div>
  )
}

export default function ProjectPage() {
  const { projectId: idParam, tab = 'board' } = useParams()
  const projectId = Number(idParam)
  const [searchParams, setSearchParams] = useSearchParams()
  const openTaskId = searchParams.get('task') ? Number(searchParams.get('task')) : null
  const [filter, setFilter] = useState<BoardFilter>({ q: '', assigneeId: null, priority: null })
  const [creating, setCreating] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { user } = useAuth()

  const board = useQuery({
    queryKey: ['board', projectId],
    queryFn: () => api.get<BoardView>(`/api/projects/${projectId}/board`),
    enabled: Number.isFinite(projectId),
  })

  // Live updates: patch the cached board in place; refetch only for structural changes.
  useSubscription(Number.isFinite(projectId) ? `/topic/projects/${projectId}` : null, (body) => {
    const event = body as BoardEvent
    if (event.type === 'PROJECT_DELETED') {
      toast.info('This project was deleted')
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
      navigate('/')
      return
    }
    const current = queryClient.getQueryData<BoardView>(['board', projectId])
    const next = current ? applyEvent(current, event) : null
    if (next) queryClient.setQueryData(['board', projectId], next)
    else void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
    if (event.type === 'MEMBERS_CHANGED' && event.actorId !== user?.id) void queryClient.invalidateQueries({ queryKey: ['members', projectId] })
    void queryClient.invalidateQueries({ queryKey: ['tasks', projectId] })
    void queryClient.invalidateQueries({ queryKey: ['activity', projectId] })
  })

  const openTask = (id: number) => setSearchParams((p) => { p.set('task', String(id)); return p })
  const closeTask = () => setSearchParams((p) => { p.delete('task'); return p })

  const stats = useMemo(() => {
    const all = board.data?.columns.flatMap((c) => c.tasks) ?? []
    return { total: all.length, done: all.filter((t) => t.completedAt).length }
  }, [board.data])

  if (board.isLoading) return <PageSpinner />
  if (board.isError) {
    const status = board.error instanceof ApiError ? board.error.status : 0
    return (
      <div className="mx-auto max-w-lg px-4 py-16">
        <EmptyState icon={<Kanban className="size-8" />} title={status === 403 ? "You don't have access to this project" : status === 404 ? 'Project not found' : 'Could not load project'}>
          <p>{errorMessage(board.error)}</p>
          <Link to="/" className="mt-3 inline-block font-medium text-brand hover:underline">Back to projects</Link>
        </EmptyState>
      </div>
    )
  }
  const data = board.data!
  const role = data.project.myRole
  const canEdit = atLeast(role, 'MEMBER')
  if (!TABS.some((t) => t.key === tab)) return <Navigate to={`/projects/${projectId}`} replace />

  return (
    <div className={clsx('flex flex-col', tab === 'board' && 'h-[calc(100vh-3.5rem)]')}>
      <div className="px-4 pb-3 pt-5">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="rounded bg-brand-soft px-1.5 py-0.5 font-mono text-xs font-semibold text-brand">{data.project.key}</span>
              <h1 className="truncate text-xl font-bold">{data.project.name}</h1>
              <span className="rounded-full border border-line px-2 py-0.5 text-[11px] text-muted">{ROLE_LABEL[role]}</span>
            </div>
            <p className="mt-0.5 text-sm text-muted">
              {stats.done}/{stats.total} done · {data.members.length} members
              {role === 'VIEWER' && ' · read-only access'}
            </p>
          </div>
          <div className="ml-auto"><Presence projectId={projectId} /></div>
        </div>
        <nav className="mt-4 flex gap-1 border-b border-line">
          {TABS.map(({ key, label, icon: Icon }) => (
            <Link
              key={key}
              to={key === 'board' ? `/projects/${projectId}` : `/projects/${projectId}/${key}`}
              className={clsx(
                '-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium',
                tab === key ? 'border-brand text-fg' : 'border-transparent text-muted hover:text-fg',
              )}
            >
              <Icon className="size-4" /> {label}
            </Link>
          ))}
        </nav>
      </div>

      {tab === 'board' && (
        <>
          <BoardToolbar board={data} filter={filter} setFilter={setFilter} canEdit={canEdit} onNew={() => setCreating(true)} />
          <div className="min-h-0 flex-1">
            <Board board={data} canEdit={canEdit} filter={filter} onOpenTask={openTask} />
          </div>
        </>
      )}
      {tab === 'list' && <ListTab board={data} onOpenTask={openTask} />}
      {tab === 'activity' && <ActivityTab projectId={projectId} />}
      {tab === 'settings' && <ProjectSettings board={data} />}

      {openTaskId !== null && <TaskModal key={openTaskId} taskId={openTaskId} board={data} onClose={closeTask} />}
      <NewTaskModal open={creating} board={data} onClose={() => setCreating(false)} onCreated={openTask} />
    </div>
  )
}
