# syntax=docker/dockerfile:1
# Frontend + Caddy trong một image: Caddy phục vụ file tĩnh, proxy /api và /ws về backend, tự lấy chứng chỉ HTTPS.
# Build (ngữ cảnh là thư mục gốc repo): docker build -f deploy/web.Dockerfile -t soudan-web .

FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /app/dist /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
