import { api } from './client'

export interface QueueInfo {
  name: string
  type: string
  durable: boolean
  messages: number
  messagesReady: number
  messagesUnacknowledged: number
  consumers: number
  state: string
}

export interface ExchangeInfo {
  name: string
  type: string
  durable: boolean
  internal: boolean
}

export interface BindingInfo {
  source: string
  destination: string
  destinationType: string
  routingKey: string
}

export interface NodeInfo {
  name: string
  type: string
  running: boolean
}

export interface NewQueue {
  name: string
  durable: boolean
  quorum: boolean
  deadLetterExchange?: string
  deadLetterRoutingKey?: string
}

export interface NewExchange {
  name: string
  type: string
  durable: boolean
}

export interface NewBinding {
  exchange: string
  queue: string
  routingKey: string
}

export const fetchQueues = () => api.get<QueueInfo[]>('/api/messaging/queues').then((r) => r.data)

export const fetchExchanges = () => api.get<ExchangeInfo[]>('/api/messaging/exchanges').then((r) => r.data)

export const fetchBindings = () => api.get<BindingInfo[]>('/api/messaging/bindings').then((r) => r.data)

export const fetchNodes = () => api.get<NodeInfo[]>('/api/messaging/nodes').then((r) => r.data)

export const createQueue = (body: NewQueue) => api.post<QueueInfo>('/api/messaging/queues', body).then((r) => r.data)

export const deleteQueue = (name: string) => api.delete(`/api/messaging/queues/${encodeURIComponent(name)}`)

export const createExchange = (body: NewExchange) =>
  api.post<ExchangeInfo>('/api/messaging/exchanges', body).then((r) => r.data)

export const deleteExchange = (name: string) => api.delete(`/api/messaging/exchanges/${encodeURIComponent(name)}`)

export const createBinding = (body: NewBinding) =>
  api.post<BindingInfo>('/api/messaging/bindings', body).then((r) => r.data)

export const deleteBinding = (exchange: string, queue: string, routingKey: string) =>
  api.delete('/api/messaging/bindings', { params: { exchange, queue, routingKey } })
