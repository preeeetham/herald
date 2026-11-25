# 01 · Problem Research

> Status: Final · Phase: Discovery · Last updated: 2026-11-25

This document records the discovery work done before any code was written. It
answers three questions: *who has the problem, what exactly hurts, and why a
new service is justified.*

## 1. Domain context

Property & Casualty (P&C) insurers run their business on core systems — policy
administration, claims and billing. Modern core platforms are moving to the
cloud in a **hybrid-tenancy** shape:

- each insurer's core application runs as an isolated, single-tenant instance;
- around it sits a layer of **multi-tenant shared cloud services** (identity,
  observability, integration, metadata) that every tenant consumes;
- the whole platform runs on **AWS**, with workloads orchestrated by
  **Kubernetes**, and tenants isolated by namespace and IAM role.

The shared-services layer is where platform teams earn their keep: one service,
built once, operated for hundreds of tenants, without one tenant ever hurting
another.

## 2. The integration problem

A core system is never alone. A single claim touches:

| Downstream system | Needs to know when… |
|---|---|
| Repair-shop network | `claim.created`, `claim.assigned` |
| Payment gateway | `payment.scheduled`, `payment.reversed` |
| Fraud-scoring vendor | `claim.created`, `claim.updated` |
| Reinsurer | `policy.bound`, `claim.reserve.changed` |
| Customer comms (SMS/email) | almost everything |

The industry has converged on **outbound business events delivered over HTTP
webhooks** (or a low-code gateway for systems that cannot expose an endpoint).
Consumers always receive a full snapshot of the entity so they never need to
call back into the core system.

### Evidence from the field

- **Migration friction.** A published cloud-migration retrospective describes a
  payment integration that relied on an inbound callback servlet which is not
  supported in the cloud; the team had to re-engineer the whole payment flow.
  Integration complexity is repeatedly named as a top cause of migration
  delays and cost overruns.
- **Stop-gap integration layers.** An AWS case study of a North-American
  insurer shows the vendor-recommended integration gateway being treated as a
  *short-term* solution while the team evaluated alternatives.
- **Post-migration ops.** Consultancies report that after moving to cloud,
  insurers struggle to *operate* the platform — they lack visibility into what
  their integrations are actually doing.
- **Agentic traffic is coming.** In October 2026 the market moved to AI agents
  running on the same platform as the core applications. Agents both emit and
  react to business events, multiplying event volume and the number of
  consumers.

### Recurring failure modes (practitioner write-ups)

1. **Noisy neighbours** — one tenant's slow or dead endpoint blocks workers and
   degrades delivery for *every* tenant on a shared pipeline.
2. **Naïve retries** — immediate retries hammer a struggling receiver; no
   distinction between permanent (`400`, `410`) and transient (`429`, `503`,
   timeouts) failures.
3. **Lost events** — events published outside the database transaction are
   lost when the process crashes between "commit" and "send".
4. **Duplicates** — at-least-once delivery without idempotency keys causes
   double payments / double SMS.
5. **No forensics** — when a partner says "we never got it", nobody can show
   the request, the response, and every retry.
6. **Spoofing & SSRF** — unsigned webhooks can be forged; user-supplied URLs
   can target internal metadata endpoints (`169.254.169.254`).

## 3. Users & jobs-to-be-done

| Persona | Job to be done |
|---|---|
| **Integration engineer at an insurer** | "Publish an event once and trust that every partner gets it, exactly as sent, even if they are down for an hour." |
| **Partner developer** (repair shop, vendor) | "Verify the event really came from the insurer and ignore duplicates." |
| **Platform / SRE engineer** | "Run one delivery service for all tenants; a single bad tenant must not page me for everyone." |
| **Support analyst** | "Find the event, see every attempt, replay it with one click." |

## 4. Existing options

| Option | Gap |
|---|---|
| Hand-rolled per-integration code | Every team re-implements retries, signing, DLQ. |
| Generic message brokers (SNS/SQS/Kafka) | Transport only — no endpoint management, signing, replay UI, or per-tenant fairness. |
| Commercial webhook SaaS | Data leaves the insurer's regulated cloud boundary; not tenant-aware for a platform provider. |

## 5. Opportunity

Build **Herald** — a *multi-tenant, self-hostable event delivery service* that
a cloud platform team can operate as a shared service for every tenant:
transactional ingestion, fair per-tenant delivery, smart retries, signed
payloads, dead-lettering with replay, and first-class observability.

## Sources

- Guidewire — *Guidewire Cloud: Maximizing Customer Value with Hybrid Tenancy*
- Guidewire Developer Blog — *Simplifying Outbound Integrations in InsuranceSuite Cloud (Parts 1–2)*
- Guidewire Developer Blog — *Lessons from Real Guidewire Cloud Migrations*
- AWS APN Blog — *How Wawanesa transformed their member experience by migrating to Guidewire Cloud*
- Guidewire Press Release, 2026-10-27 — *The Open, Secure, and Complete Core Platform for Agentic Insurance*
- Guidewire Engineering Blog — *A Day in the Life of a Cloud Common Services Engineer*
- Hookdeck — *Building a Reliable Service for Sending Webhooks*; Svix — *Webhook reliability*
- Standard Webhooks specification (standardwebhooks.com)
