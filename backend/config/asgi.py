"""ASGI config for the AI Social Game backend."""

import os
from django.core.asgi import get_asgi_application
from channels.auth import AuthMiddlewareStack
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.security.websocket import AllowedHostsOriginValidator

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.local")

default_django_application = get_asgi_application()

# Import websocket routing after Django initialises to avoid AppRegistryNotReady.
from .routing import websocket_urlpatterns  # noqa: E402

application = ProtocolTypeRouter(
    {
        "http": default_django_application,
        "websocket": AllowedHostsOriginValidator(
            AuthMiddlewareStack(
                URLRouter(websocket_urlpatterns)
            )
        ),
    }
)
