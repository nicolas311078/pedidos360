import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'

export function Layout() {
  const { user, logout, hasValidSession } = useAuth()
  const { count } = useCart()
  const navigate = useNavigate()

  const isAuthenticated = hasValidSession()
  const isOperacion = user?.role === 'ADMIN' || user?.role === 'OPERADOR'

  const handleLogout = () => {
    logout()
    // El catálogo es público, así que al salir se puede seguir explorando.
    navigate('/')
  }

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
          {isAuthenticated && (
            <>
              <NavLink to="/carrito">Carrito ({count})</NavLink>
              <NavLink to="/mis-pedidos">Mis pedidos</NavLink>
              {isOperacion && <NavLink to="/operacion/ordenes">Operación</NavLink>}
              {user?.role === 'ADMIN' && (
                <span className="admin-menu">
                  <NavLink to="/admin">Admin</NavLink>
                </span>
              )}
            </>
          )}
        </nav>
        <div className="navbar-user">
          {isAuthenticated ? (
            <>
              {user && (
                <span className="user-badge">
                  {user.username}
                  <small className={`badge-${user.role.toLowerCase()}`}>{user.role}</small>
                </span>
              )}
              <button className="btn btn-outline" onClick={handleLogout}>
                Salir
              </button>
            </>
          ) : (
            <>
              <span className="guest-badge">Modo invitado</span>
              <Link className="btn btn-outline" to="/login">
                Iniciar sesión
              </Link>
            </>
          )}
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
