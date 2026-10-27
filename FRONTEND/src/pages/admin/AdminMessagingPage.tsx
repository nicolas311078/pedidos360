import { FormEvent, useCallback, useEffect, useState } from 'react'
import {
  BindingInfo,
  ExchangeInfo,
  NodeInfo,
  QueueInfo,
  createBinding,
  createExchange,
  createQueue,
  deleteBinding,
  deleteExchange,
  deleteQueue,
  fetchBindings,
  fetchExchanges,
  fetchNodes,
  fetchQueues,
} from '../../api/messaging'

const errorMessage = (error: unknown, fallback: string) => {
  const response = (error as { response?: { data?: { error?: string } } })?.response
  return response?.data?.error ?? fallback
}

export function AdminMessagingPage() {
  const [queues, setQueues] = useState<QueueInfo[]>([])
  const [exchanges, setExchanges] = useState<ExchangeInfo[]>([])
  const [bindings, setBindings] = useState<BindingInfo[]>([])
  const [nodes, setNodes] = useState<NodeInfo[]>([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const [queueName, setQueueName] = useState('')
  const [queueQuorum, setQueueQuorum] = useState(true)
  const [queueDlq, setQueueDlq] = useState('')
  const [exchangeName, setExchangeName] = useState('')
  const [exchangeType, setExchangeType] = useState('topic')
  const [bindingExchange, setBindingExchange] = useState('')
  const [bindingQueue, setBindingQueue] = useState('')
  const [bindingKey, setBindingKey] = useState('')

  const load = useCallback(() => {
    Promise.all([fetchQueues(), fetchExchanges(), fetchBindings(), fetchNodes()])
      .then(([q, e, b, n]) => {
        setQueues(q)
        setExchanges(e)
        setBindings(b)
        setNodes(n)
        setError('')
      })
      .catch(() => setError('Error consultando el estado de RabbitMQ'))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const run = (action: () => Promise<unknown>, success: string) => {
    setError('')
    setOk('')
    action()
      .then(() => {
        setOk(success)
        load()
      })
      .catch((err) => setError(errorMessage(err, 'La operacion fallo')))
  }

  const onCreateQueue = (event: FormEvent) => {
    event.preventDefault()
    run(
      () =>
        createQueue({
          name: queueName,
          durable: true,
          quorum: queueQuorum,
          deadLetterExchange: queueDlq.trim() ? 'pedidos.dlx' : undefined,
          deadLetterRoutingKey: queueDlq.trim() ? queueDlq.trim() : undefined,
        }),
      `Cola '${queueName}' creada`
    )
    setQueueName('')
    setQueueDlq('')
  }

  const onCreateExchange = (event: FormEvent) => {
    event.preventDefault()
    run(() => createExchange({ name: exchangeName, type: exchangeType, durable: true }), `Exchange '${exchangeName}' creado`)
    setExchangeName('')
  }

  const onCreateBinding = (event: FormEvent) => {
    event.preventDefault()
    run(
      () => createBinding({ exchange: bindingExchange, queue: bindingQueue, routingKey: bindingKey }),
      'Binding creado'
    )
  }

  const appExchanges = exchanges.filter((e) => !e.name.startsWith('amq.') && e.name !== '')
  const appQueues = queues.filter((q) => !q.name.startsWith('amq.'))

  return (
    <div>
      <h2>Admin · Mensajería</h2>
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert">{ok}</div>}

      <div className="kpi-grid">
        {nodes.map((node) => (
          <div className="kpi-card" key={node.name}>
            <div className="kpi-value" style={{ fontSize: '1.2rem' }}>
              {node.name}
            </div>
            <div className="kpi-label">Nodo del cluster ({node.type})</div>
            <div className="kpi-sub">{node.running ? 'en línea' : 'detenido'}</div>
          </div>
        ))}
      </div>

      <h3>Colas</h3>
      <table className="table">
        <thead>
          <tr>
            <th>Cola</th>
            <th>Tipo</th>
            <th>Mensajes</th>
            <th>Sin ACK</th>
            <th>Consumidores</th>
            <th>Estado</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {appQueues.map((queue) => (
            <tr key={queue.name}>
              <td>{queue.name}</td>
              <td>{queue.type}</td>
              <td>{queue.messagesReady}</td>
              <td>{queue.messagesUnacknowledged}</td>
              <td>{queue.consumers}</td>
              <td>{queue.state}</td>
              <td>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() => run(() => deleteQueue(queue.name), `Cola '${queue.name}' eliminada`)}
                >
                  Eliminar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="admin-cols">
        <section className="card">
          <h3>Crear cola</h3>
          <form className="form" onSubmit={onCreateQueue}>
            <label>
              Nombre
              <input
                value={queueName}
                onChange={(e) => setQueueName(e.target.value)}
                placeholder="demo.cola"
                required
              />
            </label>
            <label>
              <input
                type="checkbox"
                checked={queueQuorum}
                onChange={(e) => setQueueQuorum(e.target.checked)}
              />
              Cola quorum (replicada en el cluster)
            </label>
            <label>
              DLQ (opcional)
              <input
                value={queueDlq}
                onChange={(e) => setQueueDlq(e.target.value)}
                placeholder="demo.cola.dlq"
              />
            </label>
            <button className="btn btn-primary" type="submit">
              Crear cola
            </button>
          </form>
        </section>

        <section className="card">
          <h3>Crear exchange</h3>
          <form className="form" onSubmit={onCreateExchange}>
            <label>
              Nombre
              <input
                value={exchangeName}
                onChange={(e) => setExchangeName(e.target.value)}
                placeholder="demo.exchange"
                required
              />
            </label>
            <label>
              Tipo
              <select value={exchangeType} onChange={(e) => setExchangeType(e.target.value)}>
                <option value="topic">topic</option>
                <option value="direct">direct</option>
                <option value="fanout">fanout</option>
                <option value="headers">headers</option>
              </select>
            </label>
            <button className="btn btn-primary" type="submit">
              Crear exchange
            </button>
          </form>
        </section>
      </div>

      <h3>Exchanges</h3>
      <table className="table">
        <thead>
          <tr>
            <th>Exchange</th>
            <th>Tipo</th>
            <th>Durable</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {appExchanges.map((exchange) => (
            <tr key={exchange.name}>
              <td>{exchange.name}</td>
              <td>{exchange.type}</td>
              <td>{exchange.durable ? 'sí' : 'no'}</td>
              <td>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() =>
                    run(() => deleteExchange(exchange.name), `Exchange '${exchange.name}' eliminado`)
                  }
                >
                  Eliminar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>Bindings</h3>
      <table className="table">
        <thead>
          <tr>
            <th>Exchange</th>
            <th>Cola</th>
            <th>Routing key</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {bindings
            .filter((b) => b.destinationType === 'queue')
            .map((binding) => (
              <tr key={`${binding.source}-${binding.destination}-${binding.routingKey}`}>
                <td>{binding.source}</td>
                <td>{binding.destination}</td>
                <td>{binding.routingKey}</td>
                <td>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={() =>
                      run(
                        () => deleteBinding(binding.source, binding.destination, binding.routingKey),
                        'Binding eliminado'
                      )
                    }
                  >
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
        </tbody>
      </table>

      <section className="card">
        <h3>Crear binding</h3>
        <form className="form" onSubmit={onCreateBinding}>
          <label>
            Exchange
            <input
              value={bindingExchange}
              onChange={(e) => setBindingExchange(e.target.value)}
              placeholder="pedidos.topic"
              required
            />
          </label>
          <label>
            Cola
            <input
              value={bindingQueue}
              onChange={(e) => setBindingQueue(e.target.value)}
              placeholder="tickets.pedidos"
              required
            />
          </label>
          <label>
            Routing key
            <input
              value={bindingKey}
              onChange={(e) => setBindingKey(e.target.value)}
              placeholder="pedido.estado.actualizado"
              required
            />
          </label>
          <button className="btn btn-primary" type="submit">
            Crear binding
          </button>
        </form>
      </section>
    </div>
  )
}
