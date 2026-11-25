export type DeliveryStatus = "PENDING" | "QUEUED" | "DELIVERING" | "SUCCEEDED" | "DEAD";
export type EndpointStatus = "ENABLED" | "DISABLED";

export interface Page<T> {
  data: T[];
  nextCursor: string | null;
}

export interface Tenant {
  id: string;
  slug: string;
  name: string;
  status: "ACTIVE" | "SUSPENDED";
  rateLimitPerSecond: number;
  rateLimitBurst: number;
  maxConcurrency: number;
  createdAt: string;
}

export interface Endpoint {
  id: string;
  url: string;
  description: string | null;
  eventTypes: string[];
  status: EndpointStatus;
  createdAt: string;
  updatedAt: string;
}

export interface EndpointWithSecret {
  endpoint: Endpoint;
  secret: string;
}

export interface HeraldEvent {
  id: string;
  type: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface Delivery {
  id: string;
  eventId: string;
  endpointId: string;
  eventType: string;
  endpointUrl: string;
  status: DeliveryStatus;
  attemptCount: number;
  nextAttemptAt: string;
  lastStatusCode: number | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface DeliveryAttempt {
  id: string;
  attemptNumber: number;
  outcome: "SUCCEEDED" | "RETRYING" | "FAILED";
  statusCode: number | null;
  error: string | null;
  responseExcerpt: string | null;
  startedAt: string;
  durationMs: number;
}

export interface EventDetail extends HeraldEvent {
  deliveries: Delivery[];
}

export interface DeliveryDetail {
  delivery: Delivery;
  attempts: DeliveryAttempt[];
}

export interface ApiKeySummary {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export interface IssuedApiKey {
  id: string;
  name: string;
  prefix: string;
  key: string;
  createdAt: string;
}

export interface Stats {
  since: string;
  bucket: string;
  events: number;
  deliveriesByStatus: Record<DeliveryStatus, number>;
  attempts: { total: number; succeeded: number; failed: number; successRate: number };
  latencyMs: { p50: number | null; p95: number | null; p99: number | null };
  series: { start: string; succeeded: number; failed: number; p95Ms: number | null }[];
  endpoints: { id: string; url: string; attempts: number; successRate: number; dead: number; lastAttemptAt: string | null }[];
}

export interface Problem {
  title?: string;
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}
