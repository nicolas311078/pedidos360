import { FormEvent, useEffect, useState } from 'react'
import {
  Category,
  createCategory,
  createProduct,
  deleteCategory,
  deleteProduct,
  fetchCategories,
  fetchProducts,
  Product,
  ProductInput,
  updateProduct
} from '../../api/catalog'

const emptyForm = { name: '', description: '', price: 0, stock: 0, categoryId: 0 }

export function AdminProductsPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [form, setForm] = useState<ProductInput>(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [newCategory, setNewCategory] = useState('')
  const [error, setError] = useState('')

  const reload = () => {
    fetchProducts().then(setProducts).catch(() => setError('Error cargando productos'))
  }

  useEffect(() => {
    fetchCategories().then(setCategories).catch(() => setError('Error cargando categorías'))
    reload()
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      if (editingId) {
        await updateProduct(editingId, form)
      } else {
        await createProduct(form)
      }
      setForm(emptyForm)
      setEditingId(null)
      reload()
    } catch (err: any) {
      setError(err.response?.data?.error || 'Error guardando el producto')
    }
  }

  const handleEdit = (product: Product) => {
    setEditingId(product.id)
    setForm({
      name: product.name,
      description: product.description ?? '',
      price: product.price,
      stock: product.stock,
      categoryId: product.category.id
    })
  }

  const handleDelete = async (id: number) => {
    if (!confirm('¿Eliminar este producto?')) return
    try {
      await deleteProduct(id)
      reload()
    } catch (err: any) {
      setError(err.response?.data?.error || 'Error eliminando el producto')
    }
  }

  const handleAddCategory = async () => {
    if (!newCategory.trim()) return
    try {
      await createCategory(newCategory.trim())
      setNewCategory('')
      setCategories(await fetchCategories())
    } catch (err: any) {
      setError(err.response?.data?.error || 'Error creando la categoría')
    }
  }

  const handleDeleteCategory = async (id: number) => {
    if (!confirm('¿Eliminar esta categoría?')) return
    try {
      await deleteCategory(id)
      setCategories(await fetchCategories())
    } catch (err: any) {
      setError(err.response?.data?.error || 'Error eliminando la categoría')
    }
  }

  return (
    <div>
      <h2>Admin · Productos</h2>

      <div className="admin-cols">
        <section className="card">
          <h3>{editingId ? `Editar producto #${editingId}` : 'Nuevo producto'}</h3>
          <form onSubmit={handleSubmit} className="form">
            <label>
              Categoría
              <select
                value={form.categoryId}
                onChange={(e) => setForm({ ...form, categoryId: Number(e.target.value) })}
                required
              >
                <option value={0}>Selecciona...</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nombre
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </label>
            <label>
              Descripción
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <label>
              Precio
              <input
                type="number"
                min={0}
                value={form.price}
                onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
                required
              />
            </label>
            <label>
              Stock
              <input
                type="number"
                min={0}
                value={form.stock}
                onChange={(e) => setForm({ ...form, stock: Number(e.target.value) })}
                required
              />
            </label>
            {error && <div className="alert alert-error">{error}</div>}
            <div className="inline-actions">
              <button type="submit" className="btn btn-primary">
                {editingId ? 'Guardar cambios' : 'Crear producto'}
              </button>
              {editingId && (
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => {
                    setEditingId(null)
                    setForm(emptyForm)
                  }}
                >
                  Cancelar
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="card">
          <h3>Categorías</h3>
          <div className="inline-actions">
            <input value={newCategory} onChange={(e) => setNewCategory(e.target.value)} placeholder="Nueva categoría" />
            <button className="btn btn-primary" onClick={handleAddCategory}>
              Agregar
            </button>
          </div>
          <ul className="category-list">
            {categories.map((c) => (
              <li key={c.id}>
                {c.name}
                <button className="btn btn-danger btn-sm" onClick={() => handleDeleteCategory(c.id)}>
                  Eliminar
                </button>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Nombre</th>
            <th>Categoría</th>
            <th>Precio</th>
            <th>Stock</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {products.map((p) => (
            <tr key={p.id}>
              <td>{p.id}</td>
              <td>{p.name}</td>
              <td>{p.category.name}</td>
              <td>{p.price}</td>
              <td>{p.stock}</td>
              <td>
                <button className="btn btn-outline btn-sm" onClick={() => handleEdit(p)}>
                  Editar
                </button>{' '}
                <button className="btn btn-danger btn-sm" onClick={() => handleDelete(p.id)}>
                  Eliminar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}