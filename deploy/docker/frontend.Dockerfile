ARG NODE_IMAGE=node:22-bookworm-slim
ARG NGINX_IMAGE=nginx:1.29-alpine

FROM ${NODE_IMAGE} AS builder

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

FROM ${NGINX_IMAGE}

COPY deploy/nginx/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
