import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, CheckCircle2, Crown, LogOut, Trash2, UserPlus } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { ROLE_LABEL } from '../lib/format'
import { atLeast, type BoardView, type ColumnView, type ProjectRole } from '../lib/types'
import { Avatar, Spinner } from './ui'

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="card p-5">
      <h2 className="font-semibold">{title}</h2>
      {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

/** Small helper: run an API call, toast on error, refresh the board on success. */
function useAction(projectId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
}

/** Keyed by the column's saved values, so a server/realtime change remounts it with fresh inputs. */
function ColumnRow({ column, index, total, board, canManage }: { column: ColumnView; index: number; total: number; board: BoardView; canManage: boolean }) {
  const projectId = board.project.id
  const action = useAction(projectId)
  const [name, setName] = useState(column.name)
  const [wip, setWip] = useState(column.wipLimit?.toString() ?? '')
  const dirty = name !== column.name || wip !== (column.wipLimit?.toString() ?? '')

  const save = (done = column.doneColumn) =>
    action.mutate(() =>
      api.put(`/api/projects/${projectId}/columns/${column.id}`, { name, wipLimit: wip ? Number(wip) : null, doneColumn: done }),
    )
  // Reorder: place after the column that should precede it (null = first).
  const moveTo = (newIndex: number) => {
    const others = board.columns.filter((c) => c.id !== column.id)
    const after = newIndex === 0 ? null : others[newIndex - 1].id
    action.mutate(() => api.post(`/api/projects/${projectId}/columns/${column.id}/move`, { afterColumnId: after }))
  }

  return (
    <div className="flex flex-wrap items-center gap-2 py-2">
      <div className="flex flex-col">
        <button className="text-muted hover:text-fg disabled:opacity-30" disabled={!canManage || index === 0} onClick={() => moveTo(index - 1)} aria-label="Move left">
          <ArrowUp className="size-3.5" />
        </button>
        <button className="text-muted hover:text-fg disabled:opacity-30" disabled={!canManage || index === total - 1} onClick={() => moveTo(index + 1)} aria-label="Move right">
          <ArrowDown className="size-3.5" />
        </button>
      </div>
      <input className="input w-44" value={name} disabled={!canManage} maxLength={50} onChange={(e) => setName(e.target.value)} aria-label="Column name" />
      <input className="input w-28" type="number" min={1} max={100} placeholder="No WIP limit" value={wip} disabled={!canManage} onChange={(e) => setWip(e.target.value)} aria-label="WIP limit" />
      <label className="flex items-center gap-1.5 text-sm" title="Tasks in a done column count as completed">
        <input type="checkbox" checked={column.doneColumn} disabled={!canManage} onChange={(e) => save(e.target.checked)} className="accent-[var(--brand)]" />
        Done column
      </label>
      <span className="text-xs text-muted">{column.tasks.length} tasks</span>
      {canManage && (
        <span className="ml-auto flex gap-1">
          {dirty && <button className="btn-primary px-2.5 py-1" onClick={() => save()} disabled={!name.trim()}>Save</button>}
          <button
            className="btn-ghost p-1.5 text-muted hover:text-danger"
            onClick={() => confirm(`Delete column "${column.name}"?`) && action.mutate(() => api.delete(`/api/projects/${projectId}/columns/${column.id}`))}
            aria-label="Delete column"
          >
            <Trash2 className="size-4" />
          </button>
        </span>
      )}
    </div>
  )
}

export default function ProjectSettings({ board }: { board: BoardView }) {
  const projectId = board.project.id
  const role = board.project.myRole
  const canManage = atLeast(role, 'ADMIN')
  const isOwner = role === 'OWNER'
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const action = useAction(projectId)

  const [details, setDetails] = useState({ name: board.project.name, description: board.project.description ?? '' })
  const [newColumn, setNewColumn] = useState('')
  const [invite, setInvite] = useState<{ email: string; role: ProjectRole }>({ email: '', role: 'MEMBER' })

  const saveDetails = (e: FormEvent) => {
    e.preventDefault()
    action.mutate(() => api.put(`/api/projects/${projectId}`, details), { onSuccess: () => toast.success('Project updated') })
  }
  const addColumn = (e: FormEvent) => {
    e.preventDefault()
    action.mutate(() => api.post(`/api/projects/${projectId}/columns`, { name: newColumn }), { onSuccess: () => setNewColumn('') })
  }
  const addMember = (e: FormEvent) => {
    e.preventDefault()
    action.mutate(() => api.post(`/api/projects/${projectId}/members`, invite), {
      onSuccess: () => {
        toast.success(`${invite.email} added`)
        setInvite({ email: '', role: 'MEMBER' })
      },
    })
  }
  const leave = () =>
    confirm('Leave this project?') &&
    action.mutate(() => api.delete(`/api/projects/${projectId}/members/${user!.id}`), {
      onSuccess: () => {
        toast.success('You left the project')
        void queryClient.invalidateQueries({ queryKey: ['projects'] })
        navigate('/')
      },
    })
  const deleteProject = () =>
    prompt(`Type the project key (${board.project.key}) to delete it permanently:`) === board.project.key &&
    action.mutate(() => api.delete(`/api/projects/${projectId}`), {
      onSuccess: () => {
        toast.success('Project deleted')
        navigate('/')
      },
    })

  const assignable: ProjectRole[] = isOwner ? ['ADMIN', 'MEMBER', 'VIEWER'] : ['MEMBER', 'VIEWER']

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 pb-10">
      <Section title="Project details">
        <form onSubmit={saveDetails} className="space-y-3">
          <input className="input" value={details.name} disabled={!canManage} maxLength={100} onChange={(e) => setDetails({ ...details, name: e.target.value })} aria-label="Project name" />
          <textarea className="input min-h-20" value={details.description} disabled={!canManage} maxLength={2000} onChange={(e) => setDetails({ ...details, description: e.target.value })} aria-label="Description" />
          {canManage && <button className="btn-primary" disabled={action.isPending}>Save</button>}
        </form>
      </Section>

      <Section title="Board columns" description="Order, WIP limits (max tasks in progress) and which column means “done”.">
        <div className="divide-y divide-line">
          {board.columns.map((c, i) => <ColumnRow key={`${c.id}:${c.name}:${c.wipLimit}`} column={c} index={i} total={board.columns.length} board={board} canManage={canManage} />)}
        </div>
        {canManage && (
          <form onSubmit={addColumn} className="mt-3 flex gap-2">
            <input className="input" placeholder="New column name" maxLength={50} value={newColumn} onChange={(e) => setNewColumn(e.target.value)} />
            <button className="btn-outline shrink-0" disabled={!newColumn.trim()}>Add column</button>
          </form>
        )}
      </Section>

      <Section title="Members" description="Viewers can only read; members can edit tasks; admins manage the board and people.">
        <div className="divide-y divide-line">
          {board.members.map((m) => {
            const editable = canManage && m.role !== 'OWNER' && (isOwner || m.role !== 'ADMIN') && m.user.id !== user?.id
            return (
              <div key={m.user.id} className="flex flex-wrap items-center gap-3 py-2.5">
                <Avatar user={m.user} size={32} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{m.user.fullName} {m.user.id === user?.id && <span className="text-muted">(you)</span>}</p>
                  <p className="truncate text-xs text-muted">{m.user.email}</p>
                </div>
                {editable ? (
                  <select
                    className="input w-auto"
                    value={m.role}
                    onChange={(e) => action.mutate(() => api.patch(`/api/projects/${projectId}/members/${m.user.id}`, { role: e.target.value }))}
                    aria-label={`Role of ${m.user.fullName}`}
                  >
                    {assignable.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                  </select>
                ) : (
                  <span className="flex items-center gap-1 rounded-full border border-line px-2.5 py-1 text-xs">
                    {m.role === 'OWNER' && <Crown className="size-3 text-amber-500" />} {ROLE_LABEL[m.role]}
                  </span>
                )}
                {isOwner && m.role !== 'OWNER' && (
                  <button
                    className="btn-ghost px-2 py-1 text-xs text-muted"
                    title="Transfer ownership"
                    onClick={() => confirm(`Make ${m.user.fullName} the owner? You'll become an admin.`) &&
                      action.mutate(() => api.post(`/api/projects/${projectId}/members/${m.user.id}/transfer-ownership`))}
                  >
                    <Crown className="size-3.5" />
                  </button>
                )}
                {editable && (
                  <button
                    className="btn-ghost p-1.5 text-muted hover:text-danger"
                    onClick={() => confirm(`Remove ${m.user.fullName}? Their tasks will be unassigned.`) &&
                      action.mutate(() => api.delete(`/api/projects/${projectId}/members/${m.user.id}`))}
                    aria-label={`Remove ${m.user.fullName}`}
                  >
                    <Trash2 className="size-4" />
                  </button>
                )}
              </div>
            )
          })}
        </div>
        {canManage && (
          <form onSubmit={addMember} className="mt-4 flex flex-wrap gap-2">
            <input className="input min-w-0 flex-1" type="email" required placeholder="teammate@company.com" value={invite.email} onChange={(e) => setInvite({ ...invite, email: e.target.value })} />
            <select className="input w-auto" value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value as ProjectRole })} aria-label="Role">
              {assignable.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
            </select>
            <button className="btn-primary" disabled={action.isPending}>
              {action.isPending ? <Spinner /> : <UserPlus className="size-4" />} Add
            </button>
            <p className="w-full text-xs text-muted">The person must already have a LiveLane account (try carol@demo.tpm or bob@demo.tpm).</p>
          </form>
        )}
      </Section>

      <Section title="Danger zone">
        <div className="flex flex-wrap gap-2">
          {!isOwner && (
            <button className="btn-outline text-danger" onClick={leave}>
              <LogOut className="size-4" /> Leave project
            </button>
          )}
          {isOwner && (
            <button className="btn-danger" onClick={deleteProject}>
              <Trash2 className="size-4" /> Delete project
            </button>
          )}
          {isOwner && <p className="flex items-center gap-1 text-xs text-muted"><CheckCircle2 className="size-3.5" /> As owner, transfer ownership before leaving.</p>}
        </div>
      </Section>
    </div>
  )
}
