import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
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
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminMessagingPage } from './pages/admin/AdminMessagingPage'
import { AdminRoute, OperationRoute, ProtectedRoute } from './routes/AuthGuards'

/**
 * El catálogo es público: cualquier visitante puede ver los productos sin
 * iniciar sesión. Las rutas que requieren una cuenta (carrito, pedidos y
 * administración) quedan detrás de ProtectedRoute / OperationRoute / AdminRoute.
 */
export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />

          <Route element={<Layout />}>
            <Route path="/" element={<CatalogPage />} />

            <Route element={<ProtectedRoute />}>
              <Route path="/carrito" element={<CartPage />} />
              <Route path="/mis-pedidos" element={<MyOrdersPage />} />

              <Route element={<OperationRoute />}>
                <Route path="/operacion/ordenes" element={<AdminOrdersPage />} />
              </Route>

              <Route element={<AdminRoute />}>
                <Route path="/admin" element={<AdminDashboardPage />} />
                <Route path="/admin/productos" element={<AdminProductsPage />} />
                <Route path="/admin/ordenes" element={<AdminOrdersPage />} />
                <Route path="/admin/usuarios" element={<AdminUsersPage />} />
                <Route path="/admin/mensajeria" element={<AdminMessagingPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </CartProvider>
    </AuthProvider>
  )
}
