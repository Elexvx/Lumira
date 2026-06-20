ARG NODE_IMAGE=node:22-bookworm-slim
ARG NGINX_IMAGE=nginx:1.29-alpine

FROM ${NODE_IMAGE} AS builder

WORKDIR /workspace/lumira-ui

ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    DID_YOU_KNOW=none \
    UMI_APP_API_PREFIX=/api

RUN corepack enable

COPY lumira-ui/package.json lumira-ui/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY lumira-ui/ ./
RUN pnpm build
RUN node scripts/adapt-cdn-assets.mjs

FROM ${NGINX_IMAGE}

COPY deploy/nginx/lumira-ui.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/lumira-ui/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
