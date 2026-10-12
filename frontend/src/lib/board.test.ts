import { describe, expect, it } from 'vitest'
import { afterTaskIdFor, applyEvent, compareTasks, matchesFilter, upsertTask } from './board'
import type { BoardView, TaskSummary } from './types'

const task = (id: number, columnId: number, position: string, version = 0): TaskSummary => ({
  id,
  projectId: 1,
  key: `T-${id}`,
  title: `Task ${id}`,
  priority: 'MEDIUM',
  columnId,
  columnName: '',
  position,
  assignee: null,
  dueDate: null,
  completedAt: null,
  commentCount: 0,
  createdAt: '',
  version,
})

const board = (): BoardView => ({
  project: {} as BoardView['project'],
  members: [],
  columns: [
    { id: 1, name: 'To Do', position: 'V', wipLimit: null, doneColumn: false, tasks: [task(1, 1, 'F'), task(2, 1, 'V')] },
    { id: 2, name: 'Done', position: 'l', wipLimit: null, doneColumn: true, tasks: [task(3, 2, 'V')] },
  ],
})

describe('board state', () => {
  it('orders by fractional key using byte order (uppercase before lowercase)', () => {
    const sorted = [task(1, 1, 'a'), task(2, 1, 'Z'), task(3, 1, 'V'), task(4, 1, 'VV')].sort(compareTasks)
    expect(sorted.map((t) => t.position)).toEqual(['V', 'VV', 'Z', 'a'])
  })

  it('moves a task between columns and keeps both columns ordered', () => {
    const next = upsertTask(board(), task(1, 2, 'l', 1))
    expect(next.columns[0].tasks.map((t) => t.id)).toEqual([2])
    expect(next.columns[1].tasks.map((t) => t.id)).toEqual([3, 1])
  })

  it('ignores realtime events older than local state', () => {
    const b = upsertTask(board(), task(1, 1, 'F', 5))
    const stale = applyEvent(b, { type: 'TASK_MOVED', projectId: 1, actorId: 1, at: '', payload: task(1, 2, 'l', 4) })
    expect(stale?.columns[0].tasks.map((t) => t.id)).toContain(1)
  })

  it('returns null for structural events so the caller refetches', () => {
    expect(applyEvent(board(), { type: 'COLUMNS_CHANGED', projectId: 1, actorId: 1, at: '', payload: {} })).toBeNull()
  })

  it('computes the anchor task for a drop index', () => {
    const list = [task(1, 1, 'F'), task(2, 1, 'V')]
    expect(afterTaskIdFor(list, 0)).toBeNull()
    expect(afterTaskIdFor(list, 1)).toBe(1)
    expect(afterTaskIdFor(list, 2)).toBe(2)
  })

  it('filters cards by text, assignee and priority', () => {
    const t = { ...task(1, 1, 'V'), title: 'Fix login bug', priority: 'HIGH' as const }
    const none = { q: '', assigneeId: null, priority: null }
    expect(matchesFilter(t, none)).toBe(true)
    expect(matchesFilter(t, { ...none, q: 'LOGIN' })).toBe(true)
    expect(matchesFilter(t, { ...none, q: 't-1' })).toBe(true) // matches the task key too
    expect(matchesFilter(t, { ...none, q: 'signup' })).toBe(false)
    expect(matchesFilter(t, { ...none, assigneeId: 'none' })).toBe(true)
    expect(matchesFilter(t, { ...none, assigneeId: 7 })).toBe(false)
    expect(matchesFilter(t, { ...none, priority: 'LOW' })).toBe(false)
  })
})
