import { useEffect, useState } from 'react'
import { fetchUsers, UserProfile } from '../../api/auth'

export function AdminUsersPage() {
  const [users, setUsers] = useState<UserProfile[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    fetchUsers().then(setUsers).catch(() => setError('Error cargando usuarios'))
  }, [])

  return (
    <div>
      <h2>Admin · Usuarios</h2>
      {error && <div className="alert alert-error">{error}</div>}

      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Usuario</th>
            <th>Email</th>
            <th>Rol</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.id}</td>
              <td>{u.username}</td>
              <td>{u.email}</td>
              <td>
                <span className={`badge-${u.role.toLowerCase()}`}>{u.role}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {users.length === 0 && !error && <p className="muted">No hay usuarios.</p>}
    </div>
  )
}