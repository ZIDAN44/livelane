import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Avatar, PageSpinner, Pagination } from '../components/ui'
import { api, errorMessage, qs } from '../lib/api'
import { useAuth } from '../lib/auth'
import { timeAgo } from '../lib/format'
import type { AdminUserView, GlobalRole, Page, SystemStats } from '../lib/types'

export default function AdminPage() {
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(q)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [q])

  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: () => api.get<SystemStats>('/api/admin/stats') })
  const users = useQuery({
    queryKey: ['admin', 'users', debounced, page],
    queryFn: () => api.get<Page<AdminUserView>>(`/api/admin/users${qs({ q: debounced, page, size: 15 })}`),
    placeholderData: (prev) => prev,
  })
  const update = useMutation({
    mutationFn: (v: { id: number; role?: GlobalRole; enabled?: boolean }) =>
      api.patch(`/api/admin/users/${v.id}`, { role: v.role, enabled: v.enabled }),
    onSuccess: () => {
      toast.success('User updated')
      void queryClient.invalidateQueries({ queryKey: ['admin'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-bold">Administration</h1>
        <p className="text-sm text-muted">System-wide overview — only visible to users with the global ADMIN role.</p>
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {stats.data &&
          ([
            ['Users', stats.data.users],
            ['Projects', stats.data.projects],
            ['Tasks', stats.data.tasks],
            ['Open tasks', stats.data.openTasks],
            ['Comments', stats.data.comments],
          ] as const).map(([label, value]) => (
            <div key={label} className="card p-4">
              <p className="text-xs text-muted">{label}</p>
              <p className="mt-1 text-2xl font-bold tabular-nums">{value}</p>
            </div>
          ))}
      </div>

      <div className="space-y-3">
        <div className="relative w-72">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted" />
          <input className="input pl-8" placeholder="Search by name or email" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        {users.isLoading ? (
          <PageSpinner />
        ) : (
          <div className="card overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="border-b border-line text-xs text-muted">
                <tr>
                  <th className="px-4 py-2 font-medium">User</th>
                  <th className="px-4 py-2 font-medium">Role</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2 font-medium">Joined</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {users.data?.content.map((u) => {
                  const self = u.id === me?.id
                  return (
                    <tr key={u.id}>
                      <td className="px-4 py-2.5">
                        <div className="flex items-center gap-3">
                          <Avatar user={u} size={30} />
                          <div className="min-w-0">
                            <p className="truncate font-medium">{u.fullName} {self && <span className="text-muted">(you)</span>}</p>
                            <p className="truncate text-xs text-muted">{u.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-2.5">
                        <select
                          className="input w-auto py-1"
                          value={u.role}
                          disabled={self}
                          onChange={(e) => update.mutate({ id: u.id, role: e.target.value as GlobalRole })}
                          aria-label={`Role of ${u.fullName}`}
                        >
                          <option value="USER">User</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                      </td>
                      <td className="px-4 py-2.5">
                        <button
                          disabled={self}
                          onClick={() => update.mutate({ id: u.id, enabled: !u.enabled })}
                          className={clsx(
                            'rounded-full px-2.5 py-1 text-xs font-medium disabled:opacity-50',
                            u.enabled ? 'bg-green-500/15 text-green-700 dark:text-green-300' : 'bg-red-500/15 text-red-700 dark:text-red-300',
                          )}
                          title={self ? undefined : u.enabled ? 'Click to disable' : 'Click to enable'}
                        >
                          {u.enabled ? 'Active' : 'Disabled'}
                        </button>
                      </td>
                      <td className="px-4 py-2.5 text-muted">{timeAgo(u.createdAt)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {users.data && <Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={setPage} />}
        <p className="text-xs text-muted">Demo accounts (@demo.tpm) are protected so the public demo keeps working.</p>
      </div>
    </div>
  )
}
