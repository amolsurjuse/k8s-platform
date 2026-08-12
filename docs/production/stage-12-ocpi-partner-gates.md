# Stage 12: OCPI partner gates

Production advertises OCPI 2.2.1 and the implemented OCPI 2.3.0 CPO sender
interfaces: Credentials, Locations, CDRs, and Tariffs. Optional modules and
receiver roles stay explicitly disabled.

Payments requires a PTP partner, sandbox conformance, signed financial-advice
replay tests, reconciliation ownership, and partner certification. Bookings also
requires a reservation-capable OCPP actuator with expiry, cancel, reconnect, and
double-booking tests. eMSP and Hub roles require receiver stores, tenant-aware
routing, message-routing headers, and partner-specific module policy.

Discovery must not advertise a module until both its implementation flag and
its independent readiness/certification flag are true.
