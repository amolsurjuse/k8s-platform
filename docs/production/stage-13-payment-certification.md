# Stage 13: payment certification readiness

Production evaluates gateway routes in shadow mode for the US and India pilot
networks. Routing enforcement and incremental authorization remain disabled, so
an unavailable or incomplete route cannot interrupt an existing charging flow.

The immutable double-entry ledger, dispute workflow, settlement import model,
and provider-pinned session payment profile are deployed. Promotion requires:

- provider certification for authorize, incremental authorize, SCA/3DS,
  stored-credential indicators, capture, void, refund, and signed webhooks;
- successful long-session hold-increase and reconnect/retry scenarios;
- an imported sandbox settlement batch with zero unexplained ledger mismatch;
- chargeback lifecycle evidence and named operations ownership;
- explicit network/merchant route approval and rollback criteria.

No provider capability is inferred from an account existing in a sandbox.
