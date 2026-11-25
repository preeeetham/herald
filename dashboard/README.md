# Herald dashboard

Tenant console for Herald: delivery health, endpoints, events, delivery forensics with an
attempt timeline, a test-event playground and API-key management.

**Stack:** Next.js 16 (App Router, Cache Components), React 19, TypeScript, Tailwind CSS 4,
[shadcn/ui](https://ui.shadcn.com) primitives (tables, dialogs, charts on Recharts) and
[Aceternity UI](https://ui.aceternity.com) components (sidebar, code block, glowing cards,
background ripple), SWR for data fetching.

## Architecture

- The browser never talks to the Herald API directly. `src/app/api/herald/[...path]/route.ts`
  is a backend-for-frontend proxy that forwards to `HERALD_API_URL` at runtime, so the API
  address is not baked into the bundle and no CORS configuration is needed in production.
- The tenant API key is kept in `sessionStorage` (tab-scoped, cleared on close) and sent as a
  bearer token; a 401 from the API signs the user out.
- Lists use cursor pagination backed by the API's time-ordered UUIDv7 ids.

## Develop

```bash
npm install
HERALD_API_URL=http://localhost:8080 npm run dev   # with the stack from deploy/docker-compose.yml
```

`npm run lint`, `npm run typecheck` and `npm run build` run in CI.
