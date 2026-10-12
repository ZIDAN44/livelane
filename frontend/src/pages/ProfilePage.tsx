import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Avatar, FieldError, Spinner } from '../components/ui'
import { ApiError, api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { UserProfile } from '../lib/types'

export default function ProfilePage() {
  const { user, setUser } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [emailNotifications, setEmailNotifications] = useState(user?.emailNotifications ?? true)
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' })
  const [pwErrors, setPwErrors] = useState<Record<string, string>>({})

  const save = useMutation({
    mutationFn: () => api.put<UserProfile>('/api/users/me', { fullName, emailNotifications }),
    onSuccess: (u) => {
      setUser(u)
      toast.success('Profile saved')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const changePassword = useMutation({
    mutationFn: () => api.post('/api/users/me/password', pw),
    onSuccess: () => {
      setPw({ currentPassword: '', newPassword: '' })
      toast.success('Password changed. Other sessions were signed out.')
    },
    onError: (e) => (e instanceof ApiError && e.errors ? setPwErrors(e.errors) : toast.error(errorMessage(e))),
  })

  if (!user) return null
  return (
    <div className="mx-auto max-w-2xl space-y-5 px-4 py-8">
      <div className="flex items-center gap-4">
        <Avatar user={user} size={56} />
        <div>
          <h1 className="text-xl font-bold">{user.fullName}</h1>
          <p className="text-sm text-muted">{user.email} · {user.role === 'ADMIN' ? 'System admin' : 'User'}</p>
        </div>
      </div>

      <form
        className="card space-y-4 p-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <h2 className="font-semibold">Profile</h2>
        <div>
          <label className="label" htmlFor="fn">Full name</label>
          <input id="fn" className="input" required maxLength={100} value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </div>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" className="mt-0.5 accent-[var(--brand)]" checked={emailNotifications} onChange={(e) => setEmailNotifications(e.target.checked)} />
          <span>
            <span className="font-medium">Email notifications</span>
            <span className="block text-muted">Assignments, comments on your tasks, and deadline reminders.</span>
          </span>
        </label>
        <button className="btn-primary" disabled={save.isPending}>{save.isPending && <Spinner />} Save</button>
      </form>

      <form
        className="card space-y-4 p-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          setPwErrors({})
          changePassword.mutate()
        }}
      >
        <h2 className="font-semibold">Change password</h2>
        <div>
          <label className="label" htmlFor="cur">Current password</label>
          <input id="cur" type="password" className="input" required autoComplete="current-password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="new">New password</label>
          <input id="new" type="password" className="input" required minLength={8} autoComplete="new-password" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} />
          <FieldError message={pwErrors.newPassword} />
        </div>
        <button className="btn-outline" disabled={changePassword.isPending}>{changePassword.isPending && <Spinner />} Update password</button>
      </form>
    </div>
  )
}
