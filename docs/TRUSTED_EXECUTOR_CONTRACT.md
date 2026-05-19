# Trusted Executor Contract

A trusted executor is an external, independently controlled system or entity with auditable identity and record publication.

## Contract requirements

1. Executor identity must be explicit and externally auditable.
2. Executor outputs must be published to an external record system.
3. External record must be retrievable by URL or immutable ID.
4. Record must bind release manifest hash and verifier hash.
5. Trust is denied when executor is absent, ambiguous, or self-minted.

## Local-only legality

Without executor evidence, status is:
- `external_trust = NONE`
- `LOCAL_EXTERNAL_ROOT_PROTOCOL_EXTERNAL_TRUST_NONE`
