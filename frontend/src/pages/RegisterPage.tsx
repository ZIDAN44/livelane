import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { FieldError, Spinner } from '../components/ui'
import { ApiError, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { AuthShell } from './LoginPage'

export default function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErrors({})
    try {
      await register(form.fullName, form.email, form.password)
      toast.success('Welcome to TPM! Create your first project to get started.')
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError && err.errors) setErrors(err.errors)
      else toast.error(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const field = (key: keyof typeof form) => ({
    value: form[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [key]: e.target.value })),
  })

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold">Create your account</h2>
      <p className="mt-1 text-sm text-muted">Free, no credit card, takes 10 seconds.</p>
      <form onSubmit={onSubmit} className="mt-6 space-y-3">
        <div>
          <label className="label" htmlFor="name">Full name</label>
          <input id="name" className="input" required maxLength={100} autoComplete="name" {...field('fullName')} />
          <FieldError message={errors.fullName} />
        </div>
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" className="input" required autoComplete="email" {...field('email')} />
          <FieldError message={errors.email} />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" className="input" required minLength={8} autoComplete="new-password" {...field('password')} />
          <p className="mt-1 text-xs text-muted">At least 8 characters, with a letter and a digit.</p>
          <FieldError message={errors.password} />
        </div>
        <button className="btn-primary w-full" disabled={busy}>
          {busy && <Spinner />} Create account
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
