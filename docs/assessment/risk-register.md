# My Risk Register

| Risk | Impact | How I control it | Residual limitation I accept |
|---|---|---|---|
| Workflow engine is a modular-monolith prototype | Medium | I persist workflow state and use an explicit dependency/state model | I am not providing a distributed scheduler |
| Demo failure injection is synthetic | Medium | I label it as demonstration-only and keep production metrics separate | It is not production reliability statistics |
| Local/private URL blocking relies on DNS resolution | Medium | I validate resolved addresses before allowing a URL | DNS rebinding needs an outbound proxy for stronger guarantees |
| AuthN/AuthZ is not implemented | High | I document the local-prototype boundary | I would add identity and authorization before internet exposure |
| In-memory rate-limit state | Medium | I apply a bounded local filter | A distributed deployment would need a shared limiter |
