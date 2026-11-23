import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Category, fetchCategories, fetchProducts, Product } from '../api/catalog'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'

export function CatalogPage() {
  const { add } = useCart()
  const { hasValidSession } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [error, setError] = useState('')

  const isAuthenticated = hasValidSession()

  useEffect(() => {
    fetchCategories().then(setCategories).catch(() => setError('Error cargando categorías'))
  }, [])

  useEffect(() => {
    fetchProducts(categoryId)
      .then(setProducts)
      .catch(() => setError('Error cargando productos'))
  }, [categoryId])

  const formatPrice = (value: number) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)

  const handleBuy = (product: Product) => {
    // Sin sesión no se puede comprar: se envía al login y se vuelve al catálogo
    // una vez autenticado (el estado "from" lo usa LoginPage).
    if (!isAuthenticated) {
      navigate('/login', {
        state: {
          from: location,
          message: 'Inicia sesión para agregar productos al carrito y realizar pedidos.'
        }
      })
      return
    }
    add(product)
  }

  return (
    <div>
      <div className="page-header">
        <h2>Catálogo</h2>
        <select value={categoryId ?? ''} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Todas las categorías</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {!isAuthenticated && (
        <div className="guest-banner">
          <span>Estás explorando como invitado. Puedes ver los productos, pero necesitas una cuenta para comprar.</span>
          <Link className="btn btn-primary btn-sm" to="/login">
            Iniciar sesión
          </Link>
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      <div className="grid">
        {products.map((product) => (
          <div className="card" key={product.id}>
            <div className="card-body">
              <h3>{product.name}</h3>
              {product.description && <p className="muted">{product.description}</p>}
              <p className="category-chip">{product.category.name}</p>
              <p className="price">{formatPrice(product.price)}</p>
              <p className="muted">
                Stock: <strong>{product.stock}</strong>
              </p>
            </div>
            <div className="card-footer">
              <button
                className="btn btn-primary btn-block"
                disabled={product.stock <= 0}
                onClick={() => handleBuy(product)}
              >
                {product.stock <= 0
                  ? 'Sin stock'
                  : isAuthenticated
                    ? 'Agregar al carrito'
                    : 'Iniciar sesión para comprar'}
              </button>
            </div>
          </div>
        ))}
      </div>

      {products.length === 0 && !error && <p className="muted">No hay productos disponibles.</p>}
    </div>
  )
}
