# OCPP Simulator Connection Ownership

## Production contract

The simulator, WebSocket connector, and OCPP service currently run with one
replica each in production. A charge-point identity has one live OCPP WebSocket
owner at a time:

1. `ocpp-simulator` reconciles fleet state from `web-socket-connector`.
2. `web-socket-connector` owns the physical client WebSocket and its active
   transaction state for that charge point.
3. `ocpp-service` owns the server-side OCPP WebSocket session and routes a
   remote command to that local session.

The connector makes repeated `/connect` calls for the same charger and endpoint
idempotent. A conflicting endpoint or OCPP version is rejected, so a retry can
never create a second socket for the same charge-point identity.

## Recovery behaviour

- Connector socket loss enters bounded exponential reconnect (1s to 30s,
  deterministic per charger to spread a fleet reconnect).
- Pending commands fail rather than replaying onto a reconnected socket.
- Active transactions re-announce charging status and the latest meter value
  after reconnect; they do not emit a duplicate start transaction.
- Simulator state is reconciled from one bulk connector snapshot. Missing,
  disconnected, or error states clear stale local `CONNECTED` projections.
- The simulator reconnects a bounded batch of 32 chargers every three seconds.
  A failed charger is retried after five seconds rather than delaying the whole
  fleet.

## Why single replica is intentional

Fleet and socket registries are in process memory today. Horizontal scaling the
simulator, connector, or OCPP service without shared ownership would allow two
pods to observe or control the same charger. Production therefore uses a
`Recreate` deployment strategy for all three workloads; a rollout never has an
overlapping second owner.

For the current 250-user burst target, vertical capacity is the safe design:
one Go simulator/connector process can hold the required 250 connections while
the OCPP Java service is measured through Prometheus/Grafana.

## Prerequisites for future horizontal scaling

Do not raise `replicaCount` above one until all of the following are deployed
together:

1. Persist charger fleet and runtime state in Redis or PostgreSQL.
2. Add a Redis lease per charge point (`simulator:charger:{id}:owner`) with TTL
   renewal and fencing token; only the lease holder may open, send, or close the
   socket.
3. Route connector commands through Redis Streams or a durable command bus to
   the lease holder.
4. Add OCPP session-owner routing so a remote command reaching any OCPP pod is
   forwarded to the pod that owns the server WebSocket.
5. Add lease-expiry and split-brain tests, then prove failover with a rolling
   restart while charging is active.

Until then, use one replica with increased CPU/memory limits rather than an HPA.
