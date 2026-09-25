import { useEffect, useState } from 'react'
import { fetchAllOrders, fetchMyOrders, Order } from '../api/orders'
import { useAuth } from '../auth/AuthContext'
import { statusLabel } from '../utils/orderStatus'

export function MyOrdersPage() {
  const { user } = useAuth()
  const [orders, setOrders] = useState<Order[]>([])
  const [error, setError] = useState('')

  const isOperacion = user?.role === 'ADMIN' || user?.role === 'OPERADOR'

  useEffect(() => {
    const loader = isOperacion ? fetchAllOrders : fetchMyOrders
    loader()
      .then(setOrders)
      .catch(() => setError('Error cargando los pedidos'))
  }, [isOperacion])

  const formatPrice = (value: number) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)

  return (
    <div>
      <h2>{isOperacion ? 'Todos los pedidos' : 'Mis pedidos'}</h2>
      {error && <div className="alert alert-error">{error}</div>}

      {orders.length === 0 && !error && <p className="muted">Aún no hay pedidos.</p>}

      {orders.map((order) => (
        <div className="card order-card" key={order.id}>
          <div className="order-head">
            <span>
              <strong>Orden #{order.id}</strong>
            </span>
            {isOperacion && <span className="muted">{order.userEmail}</span>}
            <span className={`status status-${order.status.toLowerCase()}`}>{statusLabel(order.status)}</span>
            <span className="muted">{new Date(order.createdAt).toLocaleString('es-CL')}</span>
            <strong>{formatPrice(order.total)}</strong>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Cantidad</th>
                <th>Precio</th>
                <th>Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.productName}</td>
                  <td>{item.quantity}</td>
                  <td>{formatPrice(item.price)}</td>
                  <td>{formatPrice(item.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  )
}