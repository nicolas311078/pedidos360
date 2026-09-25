import { useEffect, useState } from 'react'
import { fetchAllOrders, Order, updateOrderStatus } from '../../api/orders'

const ORDER_STATUSES = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO', 'CANCELADO']

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [error, setError] = useState('')

  const reload = () => {
    fetchAllOrders().then(setOrders).catch(() => setError('Error cargando órdenes'))
  }

  useEffect(reload, [])

  const handleStatus = async (id: number, status: string) => {
    setError('')
    try {
      await updateOrderStatus(id, status)
      reload()
    } catch (err: any) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Error actualizando el estado')
    }
  }

  const formatPrice = (value: number) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)

  return (
    <div>
      <h2>Operación · Órdenes</h2>
      {error && <div className="alert alert-error">{error}</div>}

      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Usuario</th>
            <th>Fecha</th>
            <th>Total</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id}>
              <td>{order.id}</td>
              <td>{order.userEmail}</td>
              <td>{new Date(order.createdAt).toLocaleString('es-CL')}</td>
              <td>{formatPrice(order.total)}</td>
              <td>
                <span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>
              </td>
              <td>
                <select
                  value={order.status}
                  onChange={(e) => handleStatus(order.id, e.target.value)}
                >
                  {ORDER_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {orders.length === 0 && !error && <p className="muted">No hay órdenes.</p>}
    </div>
  )
}