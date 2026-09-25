import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { hasValidSession } = useAuth()

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

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>Pedidos360</h1>
        <p className="muted">
          Inicia sesión con Microsoft o Google para acceder al sistema.
        </p>

        <button type="button" className="btn btn-microsoft btn-block" onClick={handleMicrosoft}>
          Ingresar con Microsoft
        </button>
        <button type="button" className="btn btn-google btn-block" onClick={handleGoogle}>
          Ingresar con Google
        </button>

        <p className="muted small">
          Los usuarios de Microsoft deben existir previamente en Entra ID con un
          rol asignado (Cliente, Operador o Administrador). Los de Google
          ingresan siempre como Cliente.
        </p>
      </div>
    </div>
  )
}