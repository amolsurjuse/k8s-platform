# Stage 8: OCPP high-availability acceptance

## Design

- Two OCPP replicas terminate stateful charger WebSockets.
- Redis is the authoritative, TTL-bound owner registry and cross-pod command bus.
- Rolling updates keep `maxUnavailable: 0`; the pre-stop hook drains sockets and
  returns WebSocket status 1012 so chargers reconnect.
- A PodDisruptionBudget keeps one replica available during voluntary disruption.
- Required hostname pod anti-affinity prevents both replicas from sharing one
  Kubernetes node. A topology-spread constraint additionally bounds skew as the
  cluster grows. Anti-affinity uses `pod-template-hash` matching so old and new
  ReplicaSets can overlap during a zero-downtime rolling update without allowing
  the final active ReplicaSet to co-locate.

## Production gates

1. Both replicas are Ready with zero rollout restarts and run on distinct nodes.
2. `OCPP_CLUSTER_ROUTING_ENABLED=true` and Redis owner markers are split across
   both pod identities.
3. A controlled pod replacement preserves the service and restores the complete
   fleet within the owner-marker TTL.
4. Cross-pod remote commands return the charger response, not a false success.
5. The fleet scale test ramps in bounded batches, stops on error thresholds, and
   records 5,000-10,000 authenticated WSS connections, heartbeat success,
   reconnect time, CPU, memory, and Redis latency.

The scale gate must use isolated synthetic charger identities. It must not create
production billing sessions or overload the registered operational fleet.

## Rollback

Remove the topology constraint first if a node is unavailable and a replacement
pod cannot schedule. Cluster routing and two replicas remain enabled. Returning
to one replica or disabling routing is an emergency-only rollback because it
restores the original single failure domain.
