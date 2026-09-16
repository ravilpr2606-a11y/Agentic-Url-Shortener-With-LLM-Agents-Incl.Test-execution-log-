# Security

I validate HTTP/HTTPS URLs, enforce a URL-length limit, block localhost/private/link-local destinations, apply API rate limiting, avoid placing secrets in audit payloads, and use environment-based database credentials.

I have intentionally left authentication and authorization out of this local prototype. I would not expose the service directly to an untrusted network without adding identity, authorization and stronger network-level egress controls.

I record the remaining security assumptions and trade-offs in `docs/security-assessment.md`.
