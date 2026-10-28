import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { login, register } from '../api/auth'
import { useAuth } from '../auth/AuthContext'

function getAuthErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const responseData: unknown = error.response?.data
    if (typeof responseData === 'object' && responseData !== null) {
      const data = responseData as Record<string, unknown>
      const responseError = data.error
      if (typeof responseError === 'string') return responseError

      const validationError = Object.values(data)
        .find((value): value is string => typeof value === 'string')
      if (validationError) return validationError
    }
    if (error.response?.status === 401) return 'Usuario o contraseña incorrectos.'
  }

  return 'No se pudo completar el acceso. Inténtalo nuevamente.'
}

export function LoginPage() {
  const { hasValidSession, setCredentials } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // Si ya hay una sesión válida (JWT sin expirar en localStorage) NO se
  // muestra en /login ninguna información de la sesión activa (ni correo ni
  // rol, ni opciones "continuar/cambiar de cuenta"): se redirige a la home.
  // Para entrar con otra cuenta hay que cerrar sesión primero (botón "Salir").
  if (hasValidSession()) {
    return <Navigate to="/" replace />
  }

  // Se navega con la URL ABSOLUTA del BFF público (no por el proxy de Vite):
  // así la cookie de sesión OAuth (JSESSIONID) queda en el mismo dominio que
  // el redirect_uri que Microsoft usará en el callback. Si el login arranca
  // relativo ('/oauth2/...') la cookie queda en localhost:4200 y el callback
  // (p. ej. vía API Gateway) no la recibe -> authorization_request_not_found.
  const handleMicrosoft = () => {
    const publicBff = import.meta.env.VITE_PUBLIC_BFF_URL || 'http://localhost:8080'
    window.location.href = `${publicBff}/oauth2/authorization/azure`
  }

  const handleGoogle = () => {
    const publicBff = import.meta.env.VITE_PUBLIC_BFF_URL || 'http://localhost:8080'
    window.location.href = `${publicBff}/oauth2/authorization/google`
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const response =
        mode === 'login'
          ? await login(username, password)
          : await register(username, email, password)
      await setCredentials(response.token)
      navigate('/', { replace: true })
    } catch (requestError) {
      setError(getAuthErrorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  const switchMode = (nextMode: 'login' | 'register') => {
    setMode(nextMode)
    setError('')
  }

  return (
    <main className="login-page">
      <div className="login-shell">
        <section className="login-brand-panel" aria-label="Presentación de Pedidos360">
          <div className="login-brand">
            <span className="login-brand-mark" aria-hidden="true">P</span>
            <span>Pedidos360</span>
          </div>

          <div className="login-brand-copy">
            <span className="login-eyebrow">Simple. Claro. En un solo lugar.</span>
            <h1>Gestiona tus pedidos sin complicaciones.</h1>
            <p>
              Explora el catálogo, realiza pedidos y consulta su estado desde una
              experiencia sencilla.
            </p>
          </div>

          <div className="login-visual" aria-hidden="true">
            <div className="login-visual-orbit login-visual-orbit-one" />
            <div className="login-visual-orbit login-visual-orbit-two" />
            <div className="login-order-card">
              <span className="login-order-icon">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="m3.75 7.5 8.25-4.25 8.25 4.25v9L12 21l-8.25-4.5v-9Z" />
                  <path d="m3.9 7.6 8.1 4.2 8.1-4.2M12 12v8.7M8 5.3l8.2 4.4" />
                </svg>
              </span>
              <span className="login-order-lines">
                <span />
                <span />
              </span>
              <span className="login-order-check">✓</span>
            </div>
            <div className="login-visual-caption">Todo tu proceso, más simple</div>
          </div>

          <div className="login-brand-footer">Acceso seguro para clientes y equipos.</div>
        </section>

        <section className="login-card" aria-labelledby="login-title">
          <header className="login-header">
            <span className="login-eyebrow">{mode === 'login' ? 'Bienvenido/a' : 'Únete a Pedidos360'}</span>
            <h2 id="login-title">{mode === 'login' ? 'Inicia sesión' : 'Crea tu cuenta'}</h2>
            <p className="muted">
              {mode === 'login'
                ? 'Ingresa con tu usuario y contraseña o elige un proveedor.'
                : 'Regístrate para realizar pedidos y seguir su estado.'}
            </p>
          </header>

          <form className="login-form" onSubmit={handleSubmit}>
            <label>
              Nombre de usuario
              <input
                autoComplete="username"
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                disabled={submitting}
              />
            </label>

            {mode === 'register' && (
              <label>
                Correo electrónico
                <input
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  disabled={submitting}
                />
              </label>
            )}

            <label>
              Contraseña
              <input
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={mode === 'register' ? 6 : undefined}
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={submitting}
              />
              {mode === 'register' && <span className="login-field-hint">Mínimo 6 caracteres.</span>}
            </label>

            {error && <div className="alert alert-error" role="alert">{error}</div>}

            <button type="submit" className="btn btn-primary btn-block login-submit" disabled={submitting}>
              {submitting
                ? 'Procesando...'
                : mode === 'login'
                  ? 'Ingresar con usuario'
                  : 'Crear cuenta'}
            </button>
          </form>

          <button
            type="button"
            className="login-mode-switch"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
            disabled={submitting}
          >
            {mode === 'login' ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Inicia sesión'}
          </button>

          <div className="login-divider"><span>O continúa con</span></div>

          <div className="login-options" aria-label="Opciones de inicio de sesión">
            <button type="button" className="btn btn-microsoft btn-block" onClick={handleMicrosoft}>
              <span className="login-provider-icon login-microsoft-icon" aria-hidden="true">
                <span />
                <span />
                <span />
                <span />
              </span>
              Ingresar con Microsoft
              <span className="login-button-arrow" aria-hidden="true">→</span>
            </button>
            <button type="button" className="btn btn-google btn-block" onClick={handleGoogle}>
              <span className="login-provider-icon login-google-icon" aria-hidden="true">G</span>
              Ingresar con Google
              <span className="login-button-arrow" aria-hidden="true">→</span>
            </button>
          </div>

          <aside className="login-note">
            <strong>Información de acceso</strong>
            <p className="muted small">
              Las cuentas nuevas tienen rol Cliente. Los roles de Operador y
              Administrador son asignados por el sistema; Microsoft y Google
              también están disponibles para iniciar sesión.
            </p>
          </aside>
        </section>
      </div>
    </main>
  )
}