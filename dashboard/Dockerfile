# syntax=docker/dockerfile:1.7
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S herald && adduser -S -G herald herald
COPY --from=build --chown=herald:herald /app/.next/standalone ./
COPY --from=build --chown=herald:herald /app/.next/static ./.next/static
COPY --from=build --chown=herald:herald /app/public ./public
USER herald
EXPOSE 3000
# HERALD_API_URL is read at runtime by the /api/herald proxy route.
CMD ["node", "server.js"]
