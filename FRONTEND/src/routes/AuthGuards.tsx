import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function ProtectedRoute() {
  const { hasValidSession, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="centered">Cargando...</div>
  if (!hasValidSession()) return <Navigate to="/login" state={{ from: location }} replace />
  return <Outlet />
}

/** Operación (Admin u Operador): gestión de pedidos, estados y catálogo. */
export function OperationRoute() {
  const { hasValidSession, user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="centered">Cargando...</div>
  if (!hasValidSession()) return <Navigate to="/login" state={{ from: location }} replace />
  if (user?.role !== 'ADMIN' && user?.role !== 'OPERADOR') return <Navigate to="/" replace />
  return <Outlet />
}

/** Solo administradores: usuarios y catálogo (escritura). */
export function AdminRoute() {
  const { hasValidSession, user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="centered">Cargando...</div>
  if (!hasValidSession()) return <Navigate to="/login" state={{ from: location }} replace />
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}