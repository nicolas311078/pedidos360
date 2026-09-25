import { api } from './client'

export interface Category {
  id: number
  name: string
}

export interface Product {
  id: number
  name: string
  description?: string
  price: number
  stock: number
  category: Category
}

export interface ProductInput {
  name: string
  description?: string
  price: number
  stock: number
  categoryId: number
}

export const fetchProducts = (categoryId?: number) =>
  api
    .get<Product[]>('/api/catalog/products', { params: categoryId ? { categoryId } : {} })
    .then((r) => r.data)

export const fetchCategories = () => api.get<Category[]>('/api/catalog/categories').then((r) => r.data)

export const createProduct = (input: ProductInput) =>
  api
    .post<Product>('/api/catalog/products', {
      name: input.name,
      description: input.description,
      price: input.price,
      stock: input.stock,
      category: { id: input.categoryId }
    })
    .then((r) => r.data)

export const updateProduct = (id: number, input: ProductInput) =>
  api
    .put<Product>(`/api/catalog/products/${id}`, {
      name: input.name,
      description: input.description,
      price: input.price,
      stock: input.stock,
      category: { id: input.categoryId }
    })
    .then((r) => r.data)

export const deleteProduct = (id: number) => api.delete(`/api/catalog/products/${id}`)

export const createCategory = (name: string) =>
  api.post<Category>('/api/catalog/categories', { name }).then((r) => r.data)

export const deleteCategory = (id: number) => api.delete(`/api/catalog/categories/${id}`)