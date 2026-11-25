# Error reference

Herald returns errors as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
`application/problem+json`. The `type` URI points to a section below.

## not-found
The resource does not exist **for the calling tenant**. Resources of other
tenants are reported as not found rather than forbidden, so ids cannot be probed.

## conflict
The request conflicts with current state, e.g. a tenant slug that is taken.

## invalid-request
The request is well-formed but not acceptable, e.g. an endpoint URL that targets
a private network, or an invalid event type pattern.

## validation-failed
One or more fields failed validation. `errors` maps each field to a message.

## rate-limited
The tenant exceeded its ingestion rate. Wait for `Retry-After` seconds.
