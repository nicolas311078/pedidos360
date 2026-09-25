import { useEffect, useState } from 'react'
import { Category, fetchCategories, fetchProducts, Product } from '../api/catalog'
import { useCart } from '../cart/CartContext'

export function CatalogPage() {
  const { add } = useCart()
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [error, setError] = useState('')

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
                onClick={() => add(product)}
              >
                {product.stock > 0 ? 'Agregar al carrito' : 'Sin stock'}
              </button>
            </div>
          </div>
        ))}
      </div>

      {products.length === 0 && !error && <p className="muted">No hay productos disponibles.</p>}
    </div>
  )
}