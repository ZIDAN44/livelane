import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { ApiError, api, errorMessage } from '../lib/api'
import { PRIORITIES, titleCase } from '../lib/format'
import type { BoardView, Priority, TaskDetail } from '../lib/types'
import { FieldError, Modal, Spinner } from './ui'

export default function NewTaskModal({
  open,
  board,
  onClose,
  onCreated,
}: {
  open: boolean
  board: BoardView
  onClose: () => void
  onCreated: (id: number) => void
}) {
  const projectId = board.project.id
  const firstColumn = board.columns.find((c) => !c.doneColumn) ?? board.columns[0]
  const empty = () => ({
    title: '',
    description: '',
    priority: 'MEDIUM' as Priority,
    columnId: firstColumn?.id ?? 0,
    assigneeId: '' as number | '',
    dueDate: '',
  })
  const [form, setForm] = useState(empty)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const queryClient = useQueryClient()

  useEffect(() => {
    if (open) {
      setForm(empty())
      setErrors({})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const create = useMutation({
    mutationFn: () =>
      api.post<TaskDetail>(`/api/projects/${projectId}/tasks`, {
        ...form,
        description: form.description || null,
        assigneeId: form.assigneeId === '' ? null : form.assigneeId,
        dueDate: form.dueDate || null,
      }),
    onSuccess: (t) => {
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
      toast.success(`${t.key} created`, { action: { label: 'Open', onClick: () => onCreated(t.id) } })
      onClose()
    },
    onError: (e) => (e instanceof ApiError && e.errors ? setErrors(e.errors) : toast.error(errorMessage(e))),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate()
  }

  return (
    <Modal open={open} onClose={onClose} title="New task">
      <form onSubmit={submit} className="space-y-4 p-5">
        <div>
          <label className="label" htmlFor="t-title">Title</label>
          <input id="t-title" className="input" autoFocus required maxLength={200} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          <FieldError message={errors.title} />
        </div>
        <div>
          <label className="label" htmlFor="t-desc">Description</label>
          <textarea id="t-desc" className="input min-h-24" maxLength={10000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="t-col">Status</label>
            <select id="t-col" className="input" value={form.columnId} onChange={(e) => setForm({ ...form, columnId: Number(e.target.value) })}>
              {board.columns.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="t-pri">Priority</label>
            <select id="t-pri" className="input" value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}>
              {PRIORITIES.map((p) => <option key={p} value={p}>{titleCase(p)}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="t-asg">Assignee</label>
            <select id="t-asg" className="input" value={form.assigneeId} onChange={(e) => setForm({ ...form, assigneeId: e.target.value ? Number(e.target.value) : '' })}>
              <option value="">Unassigned</option>
              {board.members.filter((m) => m.role !== 'VIEWER').map((m) => <option key={m.user.id} value={m.user.id}>{m.user.fullName}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="t-due">Due date</label>
            <input id="t-due" type="date" className="input" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={create.isPending}>
            {create.isPending && <Spinner />} Create task
          </button>
        </div>
      </form>
    </Modal>
  )
}
