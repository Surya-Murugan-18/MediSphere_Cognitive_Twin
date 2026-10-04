# =============================================================================
# MediSphere Frontend — Multi-stage Dockerfile
# Phase 8 — B8.2
#
# Stage 1 (build): node:20-alpine
#   - Installs npm dependencies
#   - Runs `vite build` — produces /app/dist
#   - VITE_API_BASE_URL is baked at build time (must be /api for Nginx proxy)
#
# Stage 2 (runtime): nginx:alpine
#   - Copies dist/ into Nginx html root
#   - Uses nginx.conf from project root (SPA fallback + /api proxy)
#
# Build args:
#   VITE_API_BASE_URL   — API base path (default: /api — via Nginx proxy)
#   VITE_WS_BASE_URL    — WebSocket base URL (default: empty — relative /ws)
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# ARGs for Vite environment variables — baked into the bundle at build time
ARG VITE_API_BASE_URL=/api
ARG VITE_WS_BASE_URL=

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_WS_BASE_URL=${VITE_WS_BASE_URL}

# Install dependencies first (layer-cached unless package files change)
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy all source files and build
COPY . .
RUN npm run build

# ── Stage 2: Runtime (Nginx) ──────────────────────────────────────────────────
FROM nginx:alpine AS runtime

# Remove default Nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom Nginx config (SPA fallback + API proxy)
COPY nginx.conf /etc/nginx/nginx.conf

# Copy Vite build output from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Nginx runs on port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
