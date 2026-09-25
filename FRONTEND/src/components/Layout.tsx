import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'

export function Layout() {
  const { user, logout } = useAuth()
  const { count } = useCart()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const isOperacion = user?.role === 'ADMIN' || user?.role === 'OPERADOR'

  return (
    <div className="app">
      <header className="navbar">
        <div className="navbar-brand">
          <Link to="/">Pedidos360</Link>
        </div>
        <nav className="navbar-links">
          <NavLink to="/" end>
            Catálogo
          </NavLink>
          <NavLink to="/carrito">Carrito ({count})</NavLink>
          <NavLink to="/mis-pedidos">Mis pedidos</NavLink>
          {isOperacion && (
            <NavLink to="/operacion/ordenes">Operación</NavLink>
          )}
          {user?.role === 'ADMIN' && (
            <span className="admin-menu">
              <NavLink to="/admin">Admin</NavLink>
            </span>
          )}
        </nav>
        <div className="navbar-user">
          {user && (
            <button className="btn btn-outline" onClick={handleLogout}>
              Salir
            </button>
          )}
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}