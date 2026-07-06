# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS builder
ARG TARGETARCH
WORKDIR /app

ARG PNPM_REGISTRY=https://registry.npmmirror.com
ARG PNPM_VERSION=9.15.9
RUN corepack enable \
    && corepack prepare "pnpm@${PNPM_VERSION}" --activate \
    && pnpm config set registry "${PNPM_REGISTRY}" \
    && pnpm config set fetch-retries 5 \
    && pnpm config set fetch-timeout 120000

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY packages/core/package.json ./packages/core/package.json
COPY packages/web/package.json ./packages/web/package.json
RUN --mount=type=cache,id=frontend-pnpm-store-${TARGETARCH},target=/root/.local/share/pnpm/store,sharing=locked \
    pnpm install --no-frozen-lockfile

COPY . .

ARG VITE_BASE_PATH=/
ARG VITE_BASE_URL=
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
ENV VITE_BASE_URL=${VITE_BASE_URL}

RUN pnpm --filter @lingzhou/web build

FROM nginx:1.27-alpine
COPY --from=builder /app/dist/ /usr/share/nginx/html
COPY ./nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
