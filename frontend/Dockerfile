FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /build/dist/ /srv/
COPY deploy/Caddyfile /etc/caddy/Caddyfile
