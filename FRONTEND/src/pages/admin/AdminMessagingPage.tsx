import { FormEvent, useCallback, useEffect, useState } from 'react'
import {
  BindingInfo,
  ExchangeInfo,
  KafkaGroupInfo,
  KafkaTopicInfo,
  NodeInfo,
  QueueInfo,
  createBinding,
  createExchange,
  createKafkaTopic,
  createQueue,
  deleteBinding,
  deleteExchange,
  deleteKafkaTopic,
  deleteQueue,
  fetchBindings,
  fetchExchanges,
  fetchKafkaBrokers,
  fetchKafkaGroups,
  fetchKafkaTopics,
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

  const [tab, setTab] = useState<'rabbit' | 'kafka'>('rabbit')
  const [kafkaTopics, setKafkaTopics] = useState<KafkaTopicInfo[]>([])
  const [kafkaGroups, setKafkaGroups] = useState<KafkaGroupInfo[]>([])
  const [kafkaBrokers, setKafkaBrokers] = useState<NodeInfo[]>([])
  const [topicName, setTopicName] = useState('')
  const [topicPartitions, setTopicPartitions] = useState(3)
  const [topicReplication, setTopicReplication] = useState(3)

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

  const loadKafka = useCallback(() => {
    Promise.all([fetchKafkaTopics(), fetchKafkaGroups(), fetchKafkaBrokers()])
      .then(([t, g, b]) => {
        setKafkaTopics(t)
        setKafkaGroups(g)
        setKafkaBrokers(b)
        setError('')
      })
      .catch(() => setError('Error consultando el estado de Kafka'))
  }, [])

  useEffect(() => {
    load()
    loadKafka()
  }, [load, loadKafka])

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

  const runKafka = (action: () => Promise<unknown>, success: string) => {
    setError('')
    setOk('')
    action()
      .then(() => {
        setOk(success)
        loadKafka()
      })
      .catch((err) => setError(errorMessage(err, 'La operacion fallo')))
  }

  const onCreateKafkaTopic = (event: FormEvent) => {
    event.preventDefault()
    runKafka(
      () =>
        createKafkaTopic({
          name: topicName.trim(),
          partitions: topicPartitions,
          replicationFactor: topicReplication,
        }),
      `Topico '${topicName}' creado`
    )
    setTopicName('')
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

      <div style={{ display: 'flex', gap: '0.5rem', margin: '1rem 0' }}>
        <button
          className={`btn ${tab === 'rabbit' ? 'btn-primary' : ''}`}
          onClick={() => setTab('rabbit')}
        >
          RabbitMQ
        </button>
        <button
          className={`btn ${tab === 'kafka' ? 'btn-primary' : ''}`}
          onClick={() => setTab('kafka')}
        >
          Kafka
        </button>
      </div>

      {tab === 'rabbit' && (
        <>
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
        </>
      )}

      {tab === 'kafka' && (
        <>
          <div className="kpi-grid">
            {kafkaBrokers.map((broker) => (
              <div className="kpi-card" key={broker.name}>
                <div className="kpi-value" style={{ fontSize: '1.2rem' }}>
                  {broker.name}
                </div>
                <div className="kpi-label">Broker de Kafka</div>
                <div className="kpi-sub">{broker.running ? 'en línea' : 'detenido'}</div>
              </div>
            ))}
          </div>

          <h3>Tópicos</h3>
          <table className="table">
            <thead>
              <tr>
                <th>Tópico</th>
                <th>Particiones</th>
                <th>Réplicas</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {kafkaTopics
                .filter((topic) => !topic.name.startsWith('_'))
                .map((topic) => (
                  <tr key={topic.name}>
                    <td>{topic.name}</td>
                    <td>{topic.partitions}</td>
                    <td>{topic.replicationFactor}</td>
                    <td>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() =>
                          runKafka(() => deleteKafkaTopic(topic.name), `Tópico '${topic.name}' eliminado`)
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
            <h3>Crear tópico</h3>
            <form className="form" onSubmit={onCreateKafkaTopic}>
              <label>
                Nombre
                <input
                  value={topicName}
                  onChange={(e) => setTopicName(e.target.value)}
                  placeholder="demo.eventos.v1"
                  required
                />
              </label>
              <label>
                Particiones
                <input
                  type="number"
                  min={1}
                  value={topicPartitions}
                  onChange={(e) => setTopicPartitions(Number(e.target.value))}
                  required
                />
              </label>
              <label>
                Réplicas
                <input
                  type="number"
                  min={1}
                  value={topicReplication}
                  onChange={(e) => setTopicReplication(Number(e.target.value))}
                  required
                />
              </label>
              <button className="btn btn-primary" type="submit">
                Crear tópico
              </button>
            </form>
          </section>

          <h3>Consumer groups</h3>
          <table className="table">
            <thead>
              <tr>
                <th>Group ID</th>
                <th>Estado</th>
                <th>Miembros</th>
                <th>Lag</th>
                <th>Tópicos</th>
              </tr>
            </thead>
            <tbody>
              {kafkaGroups.map((group) => (
                <tr key={group.groupId}>
                  <td>{group.groupId}</td>
                  <td>{group.state}</td>
                  <td>{group.members}</td>
                  <td>{group.lag}</td>
                  <td>{group.topics.join(', ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
