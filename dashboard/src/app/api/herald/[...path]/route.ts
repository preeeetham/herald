/**
 * Backend-for-frontend proxy: the browser calls same-origin /api/herald/*, and this handler
 * forwards to the Herald API configured at *runtime* (HERALD_API_URL). That keeps the API
 * address out of the client bundle and avoids CORS in deployed environments.
 */
const API_URL = process.env.HERALD_API_URL ?? "http://localhost:8080";
const FORWARDED_REQUEST_HEADERS = ["authorization", "content-type", "idempotency-key"];
const FORWARDED_RESPONSE_HEADERS = [
  "content-type",
  "retry-after",
  "ratelimit-limit",
  "ratelimit-remaining",
  "ratelimit-reset",
  "idempotent-replayed",
];

async function proxy(request: Request, ctx: RouteContext<"/api/herald/[...path]">) {
  const { path } = await ctx.params;
  const source = new URL(request.url);
  const target = `${API_URL}/${path.map(encodeURIComponent).join("/")}${source.search}`;

  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const upstream = await fetch(target, {
    method: request.method,
    headers,
    body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.text(),
    cache: "no-store",
  });

  const responseHeaders = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new Response(upstream.status === 204 ? null : await upstream.text(), {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export { proxy as GET, proxy as POST, proxy as PATCH, proxy as DELETE };
