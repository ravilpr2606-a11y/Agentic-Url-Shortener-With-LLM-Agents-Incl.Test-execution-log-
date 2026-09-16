# Risks and Trade-offs


I recorded these risks and trade-offs to make my engineering choices explicit rather than hiding them behind generic best-practice language.
1. **Modular monolith vs distributed workflow engine:** chosen for local reproducibility and demonstrability. A distributed scheduler would add infrastructure without improving the assessment proof proportionally.
2. **Persisted state in PostgreSQL:** durable and inspectable; not optimized as a high-throughput workflow store.
3. **Bounded retry:** three attempts prevent runaway autonomy; permanent failure transitions to compensation/safe-stop.
4. **Compensation vs rollback:** only claim rollback where technically reversible. The prototype uses compensation to preserve safety rather than pretending external side effects can always be undone.
5. **In-memory rate limiter:** sufficient for a single-node prototype; requires shared state for multi-node deployment.
6. **Synthetic failure injection:** useful for repeatable demonstrations, but measurements are labeled demonstration data and are not production statistics.
