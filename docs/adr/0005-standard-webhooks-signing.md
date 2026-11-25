# ADR-0005: Standard Webhooks signature scheme

- Status: Accepted
- Date: 2026-11-25

## Context
Receivers must verify authenticity and reject replays. Proprietary schemes force
every partner to write custom verification code.

## Decision
Adopt the open **Standard Webhooks** specification:
`webhook-id`, `webhook-timestamp` and `webhook-signature: v1,<base64 HMAC-SHA256>`
over `"{id}.{timestamp}.{body}"`, with `whsec_`-prefixed base64 secrets. Secret
rotation keeps the previous secret valid for 24 h and sends both signatures.

## Consequences
- + Partners can verify with existing open-source libraries in many languages.
- + Timestamp in the signed content enables replay-window checks (5 min).
- − Secrets must be recoverable for signing, so they are encrypted (AES-GCM),
  not hashed.
