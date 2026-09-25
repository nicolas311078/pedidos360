import { api } from './client'

export interface OrderItem {
  id: number
  productId: number
  productName: string
  price: number
  quantity: number
  subtotal: number
}

export interface Order {
  id: number
  userEmail: string
  status: string
  total: number
  createdAt: string
  items: OrderItem[]
}

export interface OrderLine {
  productId: number
  quantity: number
}

export const createOrder = (items: OrderLine[]) =>
  api.post<Order>('/api/orders', { items }).then((r) => r.data)

export const fetchMyOrders = () => api.get<Order[]>('/api/orders').then((r) => r.data)

export const fetchAllOrders = () => api.get<Order[]>('/api/orders/all').then((r) => r.data)

export const updateOrderStatus = (id: number, status: string) =>
  api.put<Order>(`/api/orders/${id}/status`, { status }).then((r) => r.data)