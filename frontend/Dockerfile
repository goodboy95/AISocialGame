# build stage
FROM node:22-bookworm-slim@sha256:6c74791e557ce11fc957704f6d4fe134a7bc8d6f5ca4403205b2966bd488f6b3 AS builder
WORKDIR /app
ENV NODE_ENV=development
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && corepack prepare pnpm@10.30.0 --activate && pnpm install --frozen-lockfile --prod=false
COPY . .
RUN pnpm exec vite --version && pnpm build

# runtime stage
FROM nginxinc/nginx-unprivileged:alpine3.24@sha256:334d92979f15aaecd5dd50af5105e1230e2bb70765d45b1e2f964e7c5eda81c3
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 11030
USER nginx
