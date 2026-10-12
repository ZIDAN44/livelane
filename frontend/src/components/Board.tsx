import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { arrayMove, SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CheckCircle2, MessageSquare, Plus } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { api, errorMessage } from '../lib/api'
import { afterTaskIdFor, matchesFilter, upsertTask, type BoardFilter } from '../lib/board'
import { keyBetween } from '../lib/fractional'
import type { BoardView, ColumnView, TaskDetail, TaskSummary } from '../lib/types'
import { Avatar, DueBadge, PriorityBadge, Spinner } from './ui'

type Columns = Record<string, TaskSummary[]>
const colKey = (id: number) => `col-${id}`

export function TaskCard({ task, onOpen, overlay = false }: { task: TaskSummary; onOpen?: () => void; overlay?: boolean }) {
  const done = task.completedAt !== null
  return (
    <div
      onClick={onOpen}
      className={clsx(
        'group cursor-pointer rounded-lg border border-line bg-surface p-3 text-sm shadow-sm transition hover:border-brand/60',
        overlay && 'rotate-2 shadow-xl ring-2 ring-brand/40',
      )}
    >
      <div className="mb-1.5 flex items-center gap-1.5 text-[11px] text-muted">
        {done && <CheckCircle2 className="size-3.5 text-success" />}
        <span className={clsx('font-mono', done && 'line-through')}>{task.key}</span>
      </div>
      <p className={clsx('font-medium leading-snug', done && 'text-muted')}>{task.title}</p>
      <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
        <PriorityBadge priority={task.priority} />
        <DueBadge dueDate={task.dueDate} completed={done} />
        <span className="ml-auto flex items-center gap-2">
          {task.commentCount > 0 && (
            <span className="flex items-center gap-0.5 text-xs text-muted">
              <MessageSquare className="size-3" /> {task.commentCount}
            </span>
          )}
          <Avatar user={task.assignee} size={22} />
        </span>
      </div>
    </div>
  )
}

function SortableCard({ task, disabled, onOpen }: { task: TaskSummary; disabled: boolean; onOpen: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: String(task.id),
    disabled,
  })
  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={clsx(isDragging && 'opacity-30')}
      {...attributes}
      {...listeners}
      aria-roledescription="Draggable task"
    >
      <TaskCard task={task} onOpen={onOpen} />
    </div>
  )
}

function QuickAdd({ projectId, column }: { projectId: number; column: ColumnView }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const queryClient = useQueryClient()
  const create = useMutation({
    mutationFn: () => api.post<TaskDetail>(`/api/projects/${projectId}/tasks`, { title, columnId: column.id }),
    onSuccess: () => {
      setTitle('')
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  if (!open) {
    return (
      <button className="btn-ghost w-full justify-start text-muted" onClick={() => setOpen(true)}>
        <Plus className="size-4" /> Add task
      </button>
    )
  }
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (title.trim()) create.mutate()
  }
  return (
    <form onSubmit={submit} className="space-y-2">
      <textarea
        autoFocus
        className="input min-h-16 resize-none"
        placeholder="What needs to be done?"
        maxLength={200}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) submit(e)
          if (e.key === 'Escape') setOpen(false)
        }}
      />
      <div className="flex gap-2">
        <button className="btn-primary px-2.5 py-1.5" disabled={create.isPending || !title.trim()}>
          {create.isPending && <Spinner />} Add
        </button>
        <button type="button" className="btn-ghost px-2.5 py-1.5" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function Column({
  column,
  tasks,
  projectId,
  canEdit,
  dragDisabled,
  onOpenTask,
}: {
  column: ColumnView
  tasks: TaskSummary[]
  projectId: number
  canEdit: boolean
  dragDisabled: boolean
  onOpenTask: (id: number) => void
}) {
  const { setNodeRef, isOver } = useDroppable({ id: colKey(column.id) })
  const total = column.tasks.length
  const overLimit = column.wipLimit !== null && total >= column.wipLimit
  return (
    <section className="flex w-[290px] shrink-0 flex-col rounded-xl bg-column sm:w-[300px]" aria-label={column.name}>
      <header className="flex items-center gap-2 px-3 pb-2 pt-3">
        <h3 className="truncate text-sm font-semibold">{column.name}</h3>
        {column.doneColumn && <CheckCircle2 className="size-3.5 text-success" />}
        <span
          className={clsx(
            'ml-auto rounded-full px-2 py-0.5 text-[11px] font-semibold',
            overLimit ? 'bg-red-500/15 text-red-600 dark:text-red-300' : 'bg-surface-2 text-muted',
          )}
          title={column.wipLimit ? `WIP limit ${column.wipLimit}` : undefined}
        >
          {total}
          {column.wipLimit !== null && ` / ${column.wipLimit}`}
        </span>
      </header>
      <div
        ref={setNodeRef}
        className={clsx(
          'scroll-thin flex min-h-24 flex-1 flex-col gap-2 overflow-y-auto px-2 pb-2 transition-colors',
          isOver && 'bg-brand/5',
        )}
      >
        <SortableContext items={tasks.map((t) => String(t.id))} strategy={verticalListSortingStrategy}>
          {tasks.map((t) => (
            <SortableCard key={t.id} task={t} disabled={dragDisabled} onOpen={() => onOpenTask(t.id)} />
          ))}
        </SortableContext>
        {tasks.length === 0 && (
          <div className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-line py-6 text-xs text-muted">
            {total > 0 ? 'No matching tasks' : 'Drop tasks here'}
          </div>
        )}
      </div>
      {canEdit && (
        <div className="p-2 pt-0">
          <QuickAdd projectId={projectId} column={column} />
        </div>
      )}
    </section>
  )
}

export default function Board({
  board,
  canEdit,
  filter,
  onOpenTask,
}: {
  board: BoardView
  canEdit: boolean
  filter: BoardFilter
  onOpenTask: (id: number) => void
}) {
  const projectId = board.project.id
  const queryClient = useQueryClient()
  const filtering = Boolean(filter.q || filter.assigneeId || filter.priority)
  // Indexes from a filtered view don't map to real positions, so dragging is paused while filtering.
  const dragDisabled = !canEdit || filtering

  const fromBoard = useMemo<Columns>(
    () => Object.fromEntries(board.columns.map((c) => [colKey(c.id), c.tasks.filter((t) => matchesFilter(t, filter))])),
    [board, filter],
  )
  // Local ordering exists only while a card is being dragged; otherwise the board renders straight
  // from the query cache, so server and realtime updates show up without any state syncing.
  const [dragColumns, setDragColumns] = useState<Columns | null>(null)
  const columns = dragColumns ?? fromBoard
  const [active, setActive] = useState<TaskSummary | null>(null)
  const [origin, setOrigin] = useState<string | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const findContainer = (id: string): string | undefined =>
    id.startsWith('col-') ? id : Object.keys(columns).find((k) => columns[k].some((t) => String(t.id) === id))

  const move = useMutation({
    mutationFn: (v: { taskId: number; columnId: number; afterTaskId: number | null }) =>
      api.post<TaskSummary>(`/api/tasks/${v.taskId}/move`, { columnId: v.columnId, afterTaskId: v.afterTaskId }),
    onSuccess: (task) => {
      queryClient.setQueryData<BoardView>(['board', projectId], (b) => (b ? upsertTask(b, task) : b))
    },
    onError: (e) => {
      toast.error(errorMessage(e))
      void queryClient.invalidateQueries({ queryKey: ['board', projectId] }) // roll back to server truth
    },
  })

  const onDragStart = ({ active }: DragStartEvent) => {
    const id = String(active.id)
    const container = findContainer(id)
    setOrigin(container ?? null)
    setDragColumns(fromBoard)
    setActive(container ? (columns[container].find((t) => String(t.id) === id) ?? null) : null)
  }

  // Moving across columns while dragging: transfer the card so the target column makes room.
  const onDragOver = ({ active, over }: DragOverEvent) => {
    if (!over) return
    const from = findContainer(String(active.id))
    const to = findContainer(String(over.id))
    if (!from || !to || from === to) return
    setDragColumns((current) => {
      const prev = current ?? fromBoard
      const moving = prev[from].find((t) => String(t.id) === String(active.id))
      if (!moving) return prev
      const target = prev[to]
      const overIndex = target.findIndex((t) => String(t.id) === String(over.id))
      const index = overIndex >= 0 ? overIndex : target.length
      return {
        ...prev,
        [from]: prev[from].filter((t) => t.id !== moving.id),
        [to]: [...target.slice(0, index), moving, ...target.slice(index)],
      }
    })
  }

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    const dragged = active ? String(active.id) : null
    const task = activeTaskRef(columns, dragged)
    setActive(null)
    setDragColumns(null) // from here on the (optimistically updated) cache is the source of truth
    if (!over || !dragged || !task) return

    const container = findContainer(dragged)!
    let list = columns[container]
    const oldIndex = list.findIndex((t) => String(t.id) === dragged)
    const overIndex = list.findIndex((t) => String(t.id) === String(over.id))
    const newIndex = overIndex >= 0 ? overIndex : list.length - 1
    if (oldIndex !== newIndex) list = arrayMove(list, oldIndex, newIndex)

    const columnId = Number(container.slice(4))
    const index = list.findIndex((t) => String(t.id) === dragged)
    const others = list.filter((t) => String(t.id) !== dragged)
    const sameSpot = container === origin && fromBoard[container].findIndex((t) => String(t.id) === dragged) === index
    if (sameSpot) return

    // Optimistic update with a client-computed key; the server response replaces it.
    const before = others[index - 1]?.position ?? null
    const after = others[index]?.position ?? null
    const column = board.columns.find((c) => c.id === columnId)!
    queryClient.setQueryData<BoardView>(['board', projectId], (b) =>
      b
        ? upsertTask(b, {
            ...task,
            columnId,
            columnName: column.name,
            position: keyBetween(before, after),
            completedAt: column.doneColumn ? (task.completedAt ?? new Date().toISOString()) : null,
          })
        : b,
    )
    move.mutate({ taskId: task.id, columnId, afterTaskId: afterTaskIdFor(others, index) })
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDragEnd={onDragEnd}
      onDragCancel={() => {
        setActive(null)
        setDragColumns(null)
      }}
    >
      <div className="scroll-thin flex h-full gap-3 overflow-x-auto px-4 pb-4">
        {board.columns.map((c) => (
          <Column
            key={c.id}
            column={c}
            tasks={columns[colKey(c.id)] ?? []}
            projectId={projectId}
            canEdit={canEdit}
            dragDisabled={dragDisabled}
            onOpenTask={onOpenTask}
          />
        ))}
      </div>
      <DragOverlay dropAnimation={{ duration: 180 }}>{active && <TaskCard task={active} overlay />}</DragOverlay>
    </DndContext>
  )
}

function activeTaskRef(columns: Columns, id: string | null): TaskSummary | undefined {
  if (!id) return undefined
  for (const list of Object.values(columns)) {
    const t = list.find((x) => String(x.id) === id)
    if (t) return t
  }
  return undefined
}
