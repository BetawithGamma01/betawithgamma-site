# External Root of Trust Protocol

This protocol enforces verifiable external trust for releases.

## Minimum external-proof requirements

A release may be marked externally proven only if:
- A trusted external executor performed or attested the release operation.
- An independent external record can be retrieved (not repo-local only).
- The external record binds both:
  - `release_manifest.sha256`
  - `verifier/check_external_root_of_trust.py` hash

## External pointer contract

`external_anchor/EXTERNAL_ROOT_POINTER.json` must include:
- `external_record_type`
- `external_record_url_or_id`
- `external_record_identity`
- `external_record_timestamp`
- `public_key_external_location`
- `expected_release_manifest_sha256`
- `expected_external_root_verifier_sha256`
- `retrieval_required: true`
- `repo_only_public_key_allowed: false`
- `self_minted_key_allowed: false`

## Allowed and forbidden record types

### Tier 1 (valid)
- `SIGSTORE_REKOR`
- `NOTARIZED_HASH_RECORD`

### Tier 2 (valid)
- `SIGNED_GIT_TAG_WITH_EXTERNAL_KEY`
- `PROTECTED_RELEASE_ATTESTATION`

### Invalid
- `LOCAL_KEYPAIR`
- `REPO_ONLY_PUBLIC_KEY`
- `PLACEHOLDER_SIGNATURE`
- `SELF_SIGNED_NO_EXTERNAL_IDENTITY`
- `AGENT_GENERATED_KEY`

## Mandatory negative mutation

`external_root_self_minted_key_or_repo_only_pubkey` must fail verification.
