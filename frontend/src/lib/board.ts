import type { BoardEvent, BoardView, TaskSummary } from './types'

/** Same ordering as the server: fractional key (byte order), then id as a tie-breaker. */
export function compareTasks(a: TaskSummary, b: TaskSummary) {
  if (a.position < b.position) return -1
  if (a.position > b.position) return 1
  return a.id - b.id
}

/** Inserts or replaces a task in the board, removing it from any other column, keeping order. */
export function upsertTask(board: BoardView, task: TaskSummary): BoardView {
  return {
    ...board,
    columns: board.columns.map((col) => {
      const rest = col.tasks.filter((t) => t.id !== task.id)
      if (col.id !== task.columnId) return rest.length === col.tasks.length ? col : { ...col, tasks: rest }
      return { ...col, tasks: [...rest, task].sort(compareTasks) }
    }),
  }
}

export function removeTask(board: BoardView, taskId: number): BoardView {
  return {
    ...board,
    columns: board.columns.map((col) =>
      col.tasks.some((t) => t.id === taskId) ? { ...col, tasks: col.tasks.filter((t) => t.id !== taskId) } : col,
    ),
  }
}

export function setCommentCount(board: BoardView, taskId: number, commentCount: number): BoardView {
  return {
    ...board,
    columns: board.columns.map((col) => ({
      ...col,
      tasks: col.tasks.map((t) => (t.id === taskId ? { ...t, commentCount } : t)),
    })),
  }
}

/**
 * Applies a realtime event to the cached board. Returns {@code null} when the event can't be
 * applied locally (structure changed) and the board should be refetched instead.
 */
export function applyEvent(board: BoardView, event: BoardEvent): BoardView | null {
  switch (event.type) {
    case 'TASK_CREATED':
    case 'TASK_UPDATED':
    case 'TASK_MOVED': {
      const task = event.payload as TaskSummary
      // Ignore stale events (e.g. our own optimistic state is already newer).
      const existing = board.columns.flatMap((c) => c.tasks).find((t) => t.id === task.id)
      if (existing && existing.version > task.version) return board
      return upsertTask(board, task)
    }
    case 'TASK_DELETED':
      return removeTask(board, (event.payload as { id: number }).id)
    case 'COMMENTS_CHANGED': {
      const p = event.payload as { taskId: number; commentCount: number }
      return setCommentCount(board, p.taskId, p.commentCount)
    }
    default:
      return null
  }
}

/**
 * After a drop at {@code index} of {@code tasks} (the destination column's list *without* the
 * dragged task), the server only needs the id of the card that ends up directly above it.
 */
export function afterTaskIdFor(tasksWithoutDragged: TaskSummary[], index: number): number | null {
  return index <= 0 ? null : (tasksWithoutDragged[index - 1]?.id ?? null)
}
