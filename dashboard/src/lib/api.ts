"use client";

import useSWR, { type SWRConfiguration } from "swr";
import { session } from "./session";
import type { Problem } from "./types";

export class ApiError extends Error {
  constructor(readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`);
  }

  get status() {
    return this.problem.status;
  }
}

export interface ApiResponse<T> {
  data: T;
  headers: Headers;
  status: number;
}

/** Calls the Herald API through the same-origin proxy with the session's API key. */
export async function request<T>(
  path: string,
  init: { method?: string; body?: unknown; headers?: Record<string, string>; apiKey?: string } = {},
): Promise<ApiResponse<T>> {
  const apiKey = init.apiKey ?? session.get();
  const response = await fetch(`/api/herald${path}`, {
    method: init.method ?? "GET",
    headers: {
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError({ status: response.status, ...(body ?? {}) });
  }
  return { data: body as T, headers: response.headers, status: response.status };
}

export const api = {
  get: <T>(path: string) => request<T>(path).then((r) => r.data),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: "POST", body, headers }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: "PATCH", body }).then((r) => r.data),
  delete: (path: string) => request<void>(path, { method: "DELETE" }),
};

/** SWR hook keyed by API path; pass null (or be signed out) to skip fetching. */
export function useApi<T>(path: string | null, config?: SWRConfiguration<T, ApiError>) {
  const key = path && session.get() ? path : null;
  return useSWR<T, ApiError>(key, (p: string) => api.get<T>(p), { revalidateOnFocus: true, ...config });
}
