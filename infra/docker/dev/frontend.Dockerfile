FROM node:20-bullseye

WORKDIR /workspace/frontend

# Keep npm up-to-date for local installs inside the container.
RUN npm install -g npm@latest

# Align container UID/GID with the developer environment to avoid leaving
# root-owned artifacts on bind-mounted source files.
RUN mkdir -p /workspace/frontend \
    && chown -R node:node /workspace

USER node
