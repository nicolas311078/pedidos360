export const ORDER_STATUSES = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO', 'CANCELADO']

export const STATUS_LABELS: Record<string, string> = {
  CREADO: 'Creado',
  ACEPTADO: 'Aceptado',
  EN_PREPARACION: 'En preparación',
  DESPACHADO: 'Despachado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado'
}

/** Etiqueta legible para un estado (fallback: el código tal cual). */
export const statusLabel = (status: string) => STATUS_LABELS[status] ?? status