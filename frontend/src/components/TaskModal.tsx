import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { History, MessageSquare, Pencil, RefreshCw, Trash2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { ApiError, api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { PRIORITIES, timeAgo, titleCase } from '../lib/format'
import { atLeast, type ActivityView, type BoardView, type CommentView, type Page, type Priority, type TaskDetail } from '../lib/types'
import { Avatar, DueBadge, Modal, PageSpinner, Spinner } from './ui'

interface Draft {
  title: string
  description: string
  priority: Priority
  assigneeId: number | null
  dueDate: string
}

const toDraft = (t: TaskDetail): Draft => ({
  title: t.title,
  description: t.description ?? '',
  priority: t.priority,
  assigneeId: t.assignee?.id ?? null,
  dueDate: t.dueDate ?? '',
})

function Comments({ taskId, canComment, canModerate }: { taskId: number; canComment: boolean; canModerate: boolean }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [editing, setEditing] = useState<{ id: number; body: string } | null>(null)
  const comments = useQuery({
    queryKey: ['comments', taskId],
    queryFn: () => api.get<Page<CommentView>>(`/api/tasks/${taskId}/comments?size=100`),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['comments', taskId] })

  const add = useMutation({
    mutationFn: () => api.post<CommentView>(`/api/tasks/${taskId}/comments`, { body }),
    onSuccess: () => {
      setBody('')
      void refresh()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const edit = useMutation({
    mutationFn: (c: { id: number; body: string }) => api.put(`/api/comments/${c.id}`, { body: c.body }),
    onSuccess: () => {
      setEditing(null)
      void refresh()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/comments/${id}`),
    onSuccess: () => void refresh(),
    onError: (e) => toast.error(errorMessage(e)),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (body.trim()) add.mutate()
  }

  return (
    <div className="space-y-4">
      {comments.isLoading && <Spinner />}
      {comments.data?.content.length === 0 && <p className="text-sm text-muted">No comments yet.</p>}
      {comments.data?.content.map((c) => (
        <div key={c.id} className="group flex gap-3">
          <Avatar user={c.author} size={28} />
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2 text-xs">
              <span className="font-semibold text-fg">{c.author.fullName}</span>
              <span className="text-muted">{timeAgo(c.createdAt)}{c.edited && ' · edited'}</span>
              <span className="ml-auto hidden gap-1 group-hover:flex">
                {c.author.id === user?.id && (
                  <button className="text-muted hover:text-fg" onClick={() => setEditing({ id: c.id, body: c.body })} aria-label="Edit comment">
                    <Pencil className="size-3.5" />
                  </button>
                )}
                {(c.author.id === user?.id || canModerate) && (
                  <button className="text-muted hover:text-danger" onClick={() => remove.mutate(c.id)} aria-label="Delete comment">
                    <Trash2 className="size-3.5" />
                  </button>
                )}
              </span>
            </div>
            {editing?.id === c.id ? (
              <div className="mt-1 space-y-2">
                <textarea className="input min-h-16" value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} />
                <div className="flex gap-2">
                  <button className="btn-primary px-2.5 py-1" onClick={() => edit.mutate(editing)} disabled={!editing.body.trim()}>Save</button>
                  <button className="btn-ghost px-2.5 py-1" onClick={() => setEditing(null)}>Cancel</button>
                </div>
              </div>
            ) : (
              <p className="mt-0.5 whitespace-pre-wrap break-words text-sm">{c.body}</p>
            )}
          </div>
        </div>
      ))}
      {canComment && (
        <form onSubmit={submit} className="flex gap-3 pt-2">
          {user && <Avatar user={user} size={28} />}
          <div className="flex-1 space-y-2">
            <textarea
              className="input min-h-16"
              placeholder="Write a comment… (Ctrl+Enter to send)"
              maxLength={5000}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              onKeyDown={(e) => (e.ctrlKey || e.metaKey) && e.key === 'Enter' && submit(e)}
            />
            <button className="btn-primary px-3 py-1.5" disabled={add.isPending || !body.trim()}>
              {add.isPending && <Spinner />} Comment
            </button>
          </div>
        </form>
      )}
    </div>
  )
}

function TaskHistory({ projectId, taskId }: { projectId: number; taskId: number }) {
  const history = useQuery({
    queryKey: ['activity', projectId, 'task', taskId],
    queryFn: () => api.get<Page<ActivityView>>(`/api/projects/${projectId}/activity?taskId=${taskId}&size=50`),
  })
  if (history.isLoading) return <Spinner />
  return (
    <ol className="space-y-3 border-l border-line pl-4">
      {history.data?.content.map((a) => (
        <li key={a.id} className="relative text-sm">
          <span className="absolute -left-[21px] top-1.5 size-2 rounded-full bg-line" />
          <span className="font-medium">{a.actor?.fullName ?? 'Someone'}</span> <span className="text-muted">{a.summary}</span>
          <div className="text-xs text-muted">{timeAgo(a.createdAt)}</div>
        </li>
      ))}
    </ol>
  )
}

export default function TaskModal({ taskId, board, onClose }: { taskId: number; board: BoardView; onClose: () => void }) {
  const projectId = board.project.id
  const role = board.project.myRole
  const canEdit = atLeast(role, 'MEMBER')
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<Draft | null>(null)
  const [tab, setTab] = useState<'comments' | 'history'>('comments')

  const task = useQuery({ queryKey: ['task', taskId], queryFn: () => api.get<TaskDetail>(`/api/tasks/${taskId}`) })

  // Realtime: the board summary tells us when someone else changed this task.
  const live = board.columns.flatMap((c) => c.tasks).find((t) => t.id === taskId)
  const dirty = !!(draft && task.data && JSON.stringify(draft) !== JSON.stringify(toDraft(task.data)))
  const staleRemote = !!(live && task.data && live.version > task.data.version)

  useEffect(() => {
    if (task.data && !dirty) setDraft(toDraft(task.data))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task.data])

  useEffect(() => {
    // Someone else edited/moved it and we have no local edits: pull the latest silently.
    if (staleRemote && !dirty) void task.refetch()
    if (live) void queryClient.invalidateQueries({ queryKey: ['comments', taskId] })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live?.version, live?.commentCount])

  const save = useMutation({
    mutationFn: (d: Draft) =>
      api.put<TaskDetail>(`/api/tasks/${taskId}`, {
        version: task.data!.version,
        title: d.title,
        description: d.description || null,
        priority: d.priority,
        assigneeId: d.assigneeId,
        dueDate: d.dueDate || null,
      }),
    onSuccess: (t) => {
      queryClient.setQueryData(['task', taskId], t)
      setDraft(toDraft(t))
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
      toast.success('Saved')
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409) {
        toast.error(e.message, { action: { label: 'Reload', onClick: () => void task.refetch().then((r) => r.data && setDraft(toDraft(r.data))) } })
      } else toast.error(errorMessage(e))
    },
  })

  const changeColumn = useMutation({
    mutationFn: (columnId: number) => {
      const target = board.columns.find((c) => c.id === columnId)!
      const last = target.tasks.filter((t) => t.id !== taskId).at(-1)
      return api.post(`/api/tasks/${taskId}/move`, { columnId, afterTaskId: last?.id ?? null })
    },
    onSuccess: () => {
      void task.refetch()
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/tasks/${taskId}`),
    onSuccess: () => {
      toast.success('Task deleted')
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
      onClose()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const t = task.data
  const canDelete = !!t && (t.reporter.id === user?.id || atLeast(role, 'ADMIN'))

  return (
    <Modal open onClose={onClose} wide title={t ? <span className="font-mono text-sm text-muted">{t.key}</span> : 'Task'}>
      {task.isLoading || !draft || !t ? (
        task.isError ? <p className="p-6 text-sm text-danger">{errorMessage(task.error)}</p> : <PageSpinner />
      ) : (
        <div className="grid gap-0 md:grid-cols-[1fr_260px]">
          <div className="min-w-0 space-y-5 p-5">
            {staleRemote && dirty && (
              <div className="flex items-center gap-2 rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-300">
                Someone else just updated this task.
                <button className="ml-auto flex items-center gap-1 font-medium underline" onClick={() => void task.refetch().then((r) => r.data && setDraft(toDraft(r.data)))}>
                  <RefreshCw className="size-3.5" /> Load latest
                </button>
              </div>
            )}
            <input
              className="w-full rounded-lg border border-transparent bg-transparent px-2 py-1 -mx-2 text-xl font-semibold hover:border-line focus:border-brand focus:outline-none disabled:hover:border-transparent"
              value={draft.title}
              maxLength={200}
              disabled={!canEdit}
              onChange={(e) => setDraft({ ...draft, title: e.target.value })}
              aria-label="Title"
            />
            <div>
              <label className="label" htmlFor="desc">Description</label>
              <textarea
                id="desc"
                className="input min-h-32"
                placeholder={canEdit ? 'Add more detail…' : 'No description'}
                maxLength={10000}
                disabled={!canEdit}
                value={draft.description}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
              />
            </div>
            {canEdit && dirty && (
              <div className="flex gap-2">
                <button className="btn-primary" disabled={save.isPending || !draft.title.trim()} onClick={() => save.mutate(draft)}>
                  {save.isPending && <Spinner />} Save changes
                </button>
                <button className="btn-ghost" onClick={() => setDraft(toDraft(t))}>Discard</button>
              </div>
            )}

            <div>
              <div className="mb-4 flex gap-1 border-b border-line">
                {([
                  ['comments', 'Comments', MessageSquare],
                  ['history', 'History', History],
                ] as const).map(([key, label, Icon]) => (
                  <button
                    key={key}
                    onClick={() => setTab(key)}
                    className={clsx(
                      '-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium',
                      tab === key ? 'border-brand text-fg' : 'border-transparent text-muted hover:text-fg',
                    )}
                  >
                    <Icon className="size-4" /> {label}
                  </button>
                ))}
              </div>
              {tab === 'comments' ? (
                <Comments taskId={taskId} canComment={canEdit} canModerate={atLeast(role, 'ADMIN')} />
              ) : (
                <TaskHistory projectId={projectId} taskId={taskId} />
              )}
            </div>
          </div>

          <aside className="space-y-4 border-t border-line bg-surface-2/50 p-5 md:border-l md:border-t-0">
            <div>
              <label className="label" htmlFor="status">Status</label>
              <select id="status" className="input" disabled={!canEdit || changeColumn.isPending} value={t.columnId} onChange={(e) => changeColumn.mutate(Number(e.target.value))}>
                {board.columns.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="assignee">Assignee</label>
              <select
                id="assignee"
                className="input"
                disabled={!canEdit}
                value={draft.assigneeId ?? ''}
                onChange={(e) => setDraft({ ...draft, assigneeId: e.target.value ? Number(e.target.value) : null })}
              >
                <option value="">Unassigned</option>
                {board.members.filter((m) => m.role !== 'VIEWER' || m.user.id === draft.assigneeId).map((m) => (
                  <option key={m.user.id} value={m.user.id}>{m.user.fullName}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="priority">Priority</label>
              <select id="priority" className="input" disabled={!canEdit} value={draft.priority} onChange={(e) => setDraft({ ...draft, priority: e.target.value as Priority })}>
                {PRIORITIES.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="due">Due date</label>
              <input id="due" type="date" className="input" disabled={!canEdit} value={draft.dueDate} onChange={(e) => setDraft({ ...draft, dueDate: e.target.value })} />
              <div className="mt-1.5"><DueBadge dueDate={t.dueDate} completed={t.completedAt !== null} /></div>
            </div>
            <dl className="space-y-2 border-t border-line pt-4 text-xs">
              <div className="flex items-center justify-between gap-2">
                <dt className="text-muted">Reporter</dt>
                <dd className="flex items-center gap-1.5"><Avatar user={t.reporter} size={18} /> {t.reporter.fullName}</dd>
              </div>
              <div className="flex justify-between"><dt className="text-muted">Created</dt><dd>{timeAgo(t.createdAt)}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Updated</dt><dd>{timeAgo(t.updatedAt)}</dd></div>
              {t.completedAt && <div className="flex justify-between"><dt className="text-muted">Completed</dt><dd>{timeAgo(t.completedAt)}</dd></div>}
              <div className="flex justify-between"><dt className="text-muted">Version</dt><dd className="font-mono">v{t.version}</dd></div>
            </dl>
            {canDelete && (
              <button
                className="btn w-full text-danger hover:bg-red-500/10"
                disabled={remove.isPending}
                onClick={() => confirm(`Delete ${t.key}? This can't be undone.`) && remove.mutate()}
              >
                <Trash2 className="size-4" /> Delete task
              </button>
            )}
          </aside>
        </div>
      )}
    </Modal>
  )
}
