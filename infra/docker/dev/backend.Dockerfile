FROM eclipse-temurin:21-jdk

ARG USERNAME=developer
ARG USER_UID=1000
ARG USER_GID=1000

# Install useful CLI helpers for troubleshooting inside the dev container.
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl jq \
    && rm -rf /var/lib/apt/lists/*

# Create an unprivileged user that matches the host UID/GID so bind-mounted
# source files aren't rewritten as root-owned artifacts.
RUN set -eux; \
    if ! getent group "${USER_GID}" >/dev/null; then \
        groupadd -g "${USER_GID}" "${USERNAME}"; \
    elif ! getent group "${USERNAME}" >/dev/null 2>&1; then \
        groupadd -o -g "${USER_GID}" "${USERNAME}"; \
    fi; \
    if ! id -u "${USERNAME}" >/dev/null 2>&1; then \
        useradd -m -s /bin/bash -g "${USER_GID}" "${USERNAME}"; \
    fi; \
    CURRENT_UID="$(id -u "${USERNAME}")"; \
    if [ "${CURRENT_UID}" != "${USER_UID}" ]; then \
        usermod -o -u "${USER_UID}" "${USERNAME}"; \
    fi; \
    CURRENT_GID="$(id -g "${USERNAME}")"; \
    if [ "${CURRENT_GID}" != "${USER_GID}" ]; then \
        usermod -g "${USER_GID}" "${USERNAME}"; \
    fi

WORKDIR /workspace/backend
RUN mkdir -p /workspace/backend \
    && chown -R ${USERNAME}:${USERNAME} /workspace \
    && mkdir -p /home/${USERNAME}/.m2 \
    && chown -R ${USERNAME}:${USERNAME} /home/${USERNAME}

USER ${USERNAME}
ENV HOME=/home/${USERNAME}
