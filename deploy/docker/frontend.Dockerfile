FROM node:22-bookworm-slim AS builder

WORKDIR /workspace/frontend

ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    DID_YOU_KNOW=none \
    UMI_APP_API_PREFIX=/api

RUN corepack enable

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build
RUN node scripts/adapt-cdn-assets.mjs

FROM nginx:1.29-alpine

COPY deploy/nginx/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80
