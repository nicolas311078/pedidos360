import { useEffect } from 'react'
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { CartProvider } from './cart/CartContext'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { AuthCallbackPage } from './pages/AuthCallbackPage'
import { CatalogPage } from './pages/CatalogPage'
import { CartPage } from './pages/CartPage'
import { MyOrdersPage } from './pages/MyOrdersPage'
import { AdminProductsPage } from './pages/admin/AdminProductsPage'
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage'
import { AdminUsersPage } from './pages/admin/AdminUsersPage'
import { AdminRoute, OperationRoute, ProtectedRoute } from './routes/AuthGuards'

/**
 * Al abrir la SPA en "/" con una sesión guardada (JWT en localStorage), la app
 * NO entra directo al catálogo: muestra el login ("Ya tenés una sesión iniciada"
 * con Continuar / Cambiar de cuenta). Así siempre se puede volver a elegir
 * cuenta (p. ej. cliente, operador o admin) en cada apertura.
 */
function StartGate() {
  const { hasValidSession } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  useEffect(() => {
    if (pathname === '/' && hasValidSession()) {
      navigate('/login', { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return null
}

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <StartGate />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/" element={<CatalogPage />} />
              <Route path="/carrito" element={<CartPage />} />
              <Route path="/mis-pedidos" element={<MyOrdersPage />} />

              <Route element={<OperationRoute />}>
                <Route path="/operacion/ordenes" element={<AdminOrdersPage />} />
              </Route>

              <Route element={<AdminRoute />}>
                <Route path="/admin/productos" element={<AdminProductsPage />} />
                <Route path="/admin/ordenes" element={<AdminOrdersPage />} />
                <Route path="/admin/usuarios" element={<AdminUsersPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </CartProvider>
    </AuthProvider>
  )
}