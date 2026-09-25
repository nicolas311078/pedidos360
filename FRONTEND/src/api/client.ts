import axios from 'axios'

export const TOKEN_KEY = 'pedidos360_token'

export const api = axios.create()

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/auth/')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)