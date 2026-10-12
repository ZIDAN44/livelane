import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, FolderKanban, MessageSquare, Plus, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Avatar, DueBadge, EmptyState, FieldError, Modal, PageSpinner, Pagination, PriorityBadge, Spinner } from '../components/ui'
import { ApiError, api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { ROLE_LABEL, timeAgo } from '../lib/format'
import type { Page, ProjectDetail, ProjectSummary, TaskSummary } from '../lib/types'

function NewProjectModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form, setForm] = useState({ name: '', key: '', description: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const create = useMutation({
    mutationFn: () => api.post<ProjectDetail>('/api/projects', form),
    onSuccess: (p) => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
      toast.success(`Project ${p.name} created`)
      onClose()
      navigate(`/projects/${p.id}`)
    },
    onError: (e) => (e instanceof ApiError && e.errors ? setErrors(e.errors) : toast.error(errorMessage(e))),
  })

  const onName = (name: string) =>
    setForm((f) => ({
      ...f,
      name,
      // Suggest a key from the initials until the user edits it.
      key: f.key && f.key !== suggestKey(f.name) ? f.key : suggestKey(name),
    }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setErrors({})
    create.mutate()
  }

  return (
    <Modal open={open} onClose={onClose} title="New project">
      <form onSubmit={submit} className="space-y-4 p-5">
        <div>
          <label className="label" htmlFor="p-name">Name</label>
          <input id="p-name" className="input" autoFocus required maxLength={100} value={form.name} onChange={(e) => onName(e.target.value)} placeholder="e.g. Website Relaunch" />
          <FieldError message={errors.name} />
        </div>
        <div>
          <label className="label" htmlFor="p-key">Key</label>
          <input id="p-key" className="input font-mono uppercase" required maxLength={10} value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value.toUpperCase() })} placeholder="WEB" />
          <p className="mt-1 text-xs text-muted">Prefix for task ids, e.g. {form.key || 'WEB'}-42.</p>
          <FieldError message={errors.key} />
        </div>
        <div>
          <label className="label" htmlFor="p-desc">Description</label>
          <textarea id="p-desc" className="input min-h-20" maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={create.isPending}>
            {create.isPending && <Spinner />} Create project
          </button>
        </div>
      </form>
    </Modal>
  )
}

function suggestKey(name: string) {
  const words = name.toUpperCase().replace(/[^A-Z0-9 ]/g, '').split(/\s+/).filter(Boolean)
  if (words.length === 0) return ''
  const key = words.length === 1 ? words[0].slice(0, 4) : words.map((w) => w[0]).join('').slice(0, 5)
  return /^[A-Z]/.test(key) && key.length >= 2 ? key : ''
}

function ProjectCard({ p }: { p: ProjectSummary }) {
  const pct = p.taskCount ? Math.round((p.doneCount / p.taskCount) * 100) : 0
  return (
    <Link to={`/projects/${p.id}`} className="card group flex flex-col p-5 transition hover:border-brand hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <span className="rounded bg-brand-soft px-1.5 py-0.5 font-mono text-[11px] font-semibold text-brand">{p.key}</span>
          <h3 className="mt-2 truncate font-semibold group-hover:text-brand">{p.name}</h3>
        </div>
        <span className="shrink-0 rounded-full border border-line px-2 py-0.5 text-[11px] text-muted">{ROLE_LABEL[p.myRole]}</span>
      </div>
      <p className="mt-1 line-clamp-2 min-h-10 text-sm text-muted">{p.description ?? 'No description'}</p>
      <div className="mt-4">
        <div className="mb-1 flex justify-between text-xs text-muted">
          <span>{p.doneCount}/{p.taskCount} tasks done</span>
          <span>{pct}%</span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
          <div className="h-full rounded-full bg-brand transition-all" style={{ width: `${pct}%` }} />
        </div>
      </div>
      <div className="mt-4 flex items-center justify-between text-xs text-muted">
        <span className="flex items-center gap-1"><Users className="size-3.5" /> {p.memberCount} members</span>
        <span>Updated {timeAgo(p.updatedAt)}</span>
      </div>
    </Link>
  )
}

function MyTasks() {
  const [page, setPage] = useState(0)
  const tasks = useQuery({
    queryKey: ['my-tasks', page],
    queryFn: () => api.get<Page<TaskSummary>>(`/api/tasks/assigned-to-me?page=${page}&size=8&sort=dueDate,asc`),
  })
  if (tasks.isLoading) return <PageSpinner />
  const data = tasks.data
  if (!data || data.totalElements === 0) {
    return (
      <EmptyState icon={<CheckCircle2 className="size-8" />} title="Nothing assigned to you">
        Tasks assigned to you across all projects will show up here.
      </EmptyState>
    )
  }
  return (
    <div className="space-y-3">
      <div className="card divide-y divide-line">
        {data.content.map((t) => (
          <Link
            key={t.id}
            to={`/projects/${t.projectId}?task=${t.id}`}
            className="flex items-center gap-3 px-4 py-3 text-sm hover:bg-surface-2"
          >
            <span className="w-16 shrink-0 font-mono text-xs text-muted">{t.key}</span>
            <span className="min-w-0 flex-1 truncate font-medium">{t.title}</span>
            <span className="hidden text-xs text-muted sm:inline">{t.columnName}</span>
            {t.commentCount > 0 && (
              <span className="flex items-center gap-0.5 text-xs text-muted"><MessageSquare className="size-3" />{t.commentCount}</span>
            )}
            <PriorityBadge priority={t.priority} />
            <DueBadge dueDate={t.dueDate} completed={t.completedAt !== null} />
          </Link>
        ))}
      </div>
      <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
    </div>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const [creating, setCreating] = useState(false)
  const projects = useQuery({ queryKey: ['projects'], queryFn: () => api.get<ProjectSummary[]>('/api/projects') })

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Hi {user?.fullName.split(' ')[0]} 👋</h1>
          <p className="text-sm text-muted">Here's what's going on across your projects.</p>
        </div>
        <button className="btn-primary" onClick={() => setCreating(true)}>
          <Plus className="size-4" /> New project
        </button>
      </div>

      <section className="mt-8">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">Projects</h2>
        {projects.isLoading ? (
          <PageSpinner />
        ) : projects.data?.length === 0 ? (
          <EmptyState icon={<FolderKanban className="size-8" />} title="No projects yet">
            <button className="mt-2 font-medium text-brand hover:underline" onClick={() => setCreating(true)}>
              Create your first project
            </button>
          </EmptyState>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {projects.data?.map((p) => <ProjectCard key={p.id} p={p} />)}
          </div>
        )}
      </section>

      <section className="mt-10">
        <div className="mb-3 flex items-center gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">Assigned to me</h2>
          {user && <Avatar user={user} size={18} />}
        </div>
        <MyTasks />
      </section>

      <NewProjectModal open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}
