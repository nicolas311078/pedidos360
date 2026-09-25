import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function AuthCallbackPage() {
  const [params] = useSearchParams()
  const { setCredentials } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')

  useEffect(() => {
    const token = params.get('token')
    if (token) {
      setCredentials(token)
        .then(() => navigate('/', { replace: true }))
        .catch(() => {
          setError('No se pudo completar el inicio de sesión con Microsoft.')
          navigate('/login', { replace: true })
        })
    } else {
      setError('No se recibió el token de inicio de sesión.')
      navigate('/login', { replace: true })
    }
  }, [params, setCredentials, navigate])

  if (error) return <div className="centered">{error}</div>
  return <div className="centered">Completando inicio de sesión...</div>
}