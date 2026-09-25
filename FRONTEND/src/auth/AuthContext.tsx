import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchProfile, UserProfile } from '../api/auth'
import { TOKEN_KEY } from '../api/client'

interface AuthState {
  token: string | null
  user: UserProfile | null
  loading: boolean
  /** true si hay un JWT en localStorage que aún no expiró */
  hasValidSession: () => boolean
  setCredentials: (token: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

/** Decodifica el payload del JWT (solo lectura, no valida firma) y devuelve exp. */
function getTokenExp(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    return typeof json?.exp === 'number' ? json.exp * 1000 : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState<boolean>(!!localStorage.getItem(TOKEN_KEY))

  useEffect(() => {
    if (token && !user) {
      fetchProfile()
        .then(setUser)
        .catch(() => {
          localStorage.removeItem(TOKEN_KEY)
          setToken(null)
        })
        .finally(() => setLoading(false))
    } else {
      setLoading(false)
    }
  }, [token, user])

  const hasValidSession = useCallback(() => {
    const stored = localStorage.getItem(TOKEN_KEY)
    if (!stored) return false
    const exp = getTokenExp(stored)
    return exp !== null && exp > Date.now()
  }, [])

  const setCredentials = useCallback(async (newToken: string) => {
    localStorage.setItem(TOKEN_KEY, newToken)
    setToken(newToken)
    setLoading(true)
    try {
      const profile = await fetchProfile()
      setUser(profile)
    } catch (err) {
      // Token rechazado por el backend (p. ej. clave JWT rotada): limpiar para
      // no quedar "pegado" en la pantalla de sesión iniciada.
      localStorage.removeItem(TOKEN_KEY)
      setToken(null)
      setUser(null)
      throw err
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ token, user, loading, hasValidSession, setCredentials, logout }),
    [token, user, loading, hasValidSession, setCredentials, logout]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}