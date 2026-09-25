import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchCategories, fetchProducts, Product } from '../../api/catalog'
import { fetchUsers, UserProfile } from '../../api/auth'
import { fetchAllOrders, Order } from '../../api/orders'
import { ORDER_STATUSES, statusLabel } from '../../utils/orderStatus'

const formatPrice = (value: number) =>
  new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)

export function AdminDashboardPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [categoryCount, setCategoryCount] = useState(0)
  const [users, setUsers] = useState<UserProfile[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([fetchProducts(), fetchCategories(), fetchUsers(), fetchAllOrders()])
      .then(([p, c, u, o]) => {
        setProducts(p)
        setCategoryCount(c.length)
        setUsers(u)
        setOrders(o)
      })
      .catch(() => setError('Error cargando las métricas del dashboard'))
  }, [])

  const outOfStock = products.filter((p) => p.stock <= 0).length
  const lowStock = products.filter((p) => p.stock > 0 && p.stock <= 5).length
  const billed = orders.filter((o) => o.status !== 'CANCELADO')
  const revenue = billed.reduce((acc, o) => acc + o.total, 0)
  const pending = orders.filter((o) => o.status !== 'ENTREGADO' && o.status !== 'CANCELADO').length
  const statusCounts = ORDER_STATUSES.map((s) => ({
    status: s,
    count: orders.filter((o) => o.status === s).length
  }))
  const recent = [...orders]
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() || b.id - a.id
    )
    .slice(0, 5)

  return (
    <div>
      <h2>Admin · Dashboard</h2>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-value">{products.length}</div>
          <div className="kpi-label">Productos</div>
          <div className="kpi-sub">{categoryCount} categorías</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{users.length}</div>
          <div className="kpi-label">Usuarios</div>
          <div className="kpi-sub">registrados en el sistema</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{orders.length}</div>
          <div className="kpi-label">Órdenes</div>
          <div className="kpi-sub">{pending} en curso</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{formatPrice(revenue)}</div>
          <div className="kpi-label">Ingresos</div>
          <div className="kpi-sub">{billed.length} órdenes facturadas</div>
        </div>
      </div>

      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-value">{outOfStock}</div>
          <div className="kpi-label">Productos sin stock</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-value">{lowStock}</div>
          <div className="kpi-label">Stock bajo (≤ 5)</div>
        </div>
      </div>

      <div className="admin-cols">
        <section className="card">
          <h3>Órdenes por estado</h3>
          <ul className="status-dist">
            {statusCounts.map(({ status, count }) => (
              <li key={status}>
                <span className={`status status-${status.toLowerCase()}`}>
                  {statusLabel(status)}
                </span>
                <span className="status-count">{count}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h3>Accesos rápidos</h3>
          <div className="quick-links">
            <Link to="/admin/productos" className="btn btn-outline btn-block">
              Gestionar productos
            </Link>
            <Link to="/admin/ordenes" className="btn btn-outline btn-block">
              Ver todas las órdenes
            </Link>
            <Link to="/admin/usuarios" className="btn btn-outline btn-block">
              Administrar usuarios
            </Link>
          </div>
        </section>
      </div>

      <h3>Últimas órdenes</h3>
      {recent.length === 0 && !error ? (
        <p className="muted">No hay órdenes todavía.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Usuario</th>
              <th>Fecha</th>
              <th>Total</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {recent.map((order) => (
              <tr key={order.id}>
                <td>{order.id}</td>
                <td>{order.userEmail}</td>
                <td>{new Date(order.createdAt).toLocaleString('es-CL')}</td>
                <td>{formatPrice(order.total)}</td>
                <td>
                  <span className={`status status-${order.status.toLowerCase()}`}>
                    {order.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}