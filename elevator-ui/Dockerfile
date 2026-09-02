# Multi-stage build: compile the TypeScript client scripts, then bake
# the result into a Caddy image that serves it directly -- see
# docs/architecture.md's "elevator-ui: front-end only, no BFF" section.
# There is no server-side logic left at all: every request this image
# answers is either a static file or (via the repo root's own Caddyfile,
# mounted at runtime, unrelated to this image) a reverse proxy to
# elevator-api.

FROM node:22-slim AS build
WORKDIR /app

COPY package.json package-lock.json tsconfig.json ./
RUN npm install

COPY src ./src
COPY public ./public
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /app/public /srv
