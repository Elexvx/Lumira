ARG NODE_IMAGE=node:22-bookworm-slim
ARG NGINX_IMAGE=nginx:1.29-alpine

FROM ${NODE_IMAGE} AS builder

WORKDIR /workspace/lumira-ui

ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    DID_YOU_KNOW=none \
    UMI_APP_API_PREFIX=/api

RUN corepack enable

COPY lumira-ui/package.json lumira-ui/pnpm-lock.yaml ./
RUN --mount=type=cache,id=lumira-pnpm-store,target=/pnpm/store,sharing=locked \
    pnpm config set store-dir /pnpm/store \
    && pnpm install --frozen-lockfile

COPY lumira-ui/ ./

# Volatile release metadata belongs after dependency installation so a new
# deployment identity does not invalidate the pnpm layer.
ARG FRONTEND_VERSION=0.1.0
ARG BUILD_TIME=
ARG GIT_COMMIT=
ARG GIT_BRANCH=

ENV UMI_APP_FRONTEND_VERSION=${FRONTEND_VERSION} \
    UMI_APP_BUILD_TIME=${BUILD_TIME} \
    UMI_APP_GIT_COMMIT=${GIT_COMMIT} \
    UMI_APP_GIT_BRANCH=${GIT_BRANCH}

RUN pnpm build

FROM ${NGINX_IMAGE}

COPY deploy/nginx/lumira-ui.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/lumira-ui/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
