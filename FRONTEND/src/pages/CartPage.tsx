import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createOrder } from '../api/orders'
import { useCart } from '../cart/CartContext'

export function CartPage() {
  const { lines, total, setQuantity, remove, clear } = useCart()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const formatPrice = (value: number) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)

  const handleCheckout = async () => {
    setError('')
    setSubmitting(true)
    try {
      await createOrder(lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })))
      clear()
      navigate('/mis-pedidos')
    } catch (err: any) {
      setError(err.response?.data?.error || 'No se pudo crear la orden')
    } finally {
      setSubmitting(false)
    }
  }

  if (lines.length === 0) {
    return <p className="muted">Tu carrito está vacío.</p>
  }

  return (
    <div>
      <h2>Carrito</h2>
      <table className="table">
        <thead>
          <tr>
            <th>Producto</th>
            <th>Precio</th>
            <th>Cantidad</th>
            <th>Subtotal</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.product.id}>
              <td>{line.product.name}</td>
              <td>{formatPrice(line.product.price)}</td>
              <td>
                <input
                  type="number"
                  min={1}
                  max={line.product.stock}
                  value={line.quantity}
                  onChange={(e) => setQuantity(line.product.id, Number(e.target.value))}
                  className="qty"
                />
              </td>
              <td>{formatPrice(line.product.price * line.quantity)}</td>
              <td>
                <button className="btn btn-danger" onClick={() => remove(line.product.id)}>
                  Quitar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="checkout">
        <p className="total">
          Total: <strong>{formatPrice(total)}</strong>
        </p>
        <button className="btn btn-primary" onClick={handleCheckout} disabled={submitting}>
          {submitting ? 'Procesando...' : 'Realizar pedido'}
        </button>
      </div>
    </div>
  )
}