# Trading OS v1.0 Doctrine

## External-root-first principle

Production trust claims are forbidden unless both conditions are true:
1. A trusted external executor exists.
2. An independent external record exists and binds the release manifest hash and verifier hash.

Until those conditions are met, `external_trust` is `NONE` and the platform remains blocked for runtime, feed, signals, and broker-write.

## Explicit prohibitions

- No production private signing key generation in-repo.
- No self-signed trust root.
- No private key material stored anywhere in this repository.
- No repo-only public key used as external trust evidence.
- No `PASS_*_EXTERNALLY_PROVEN` output before trusted external evidence exists.

## Legal pre-proof status

`LOCAL_EXTERNAL_ROOT_PROTOCOL_EXTERNAL_TRUST_NONE`
