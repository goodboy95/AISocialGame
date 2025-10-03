import pytest
from channels.db import database_sync_to_async
from channels.testing import WebsocketCommunicator
from rest_framework_simplejwt.tokens import AccessToken

from apps.rooms import services
from apps.users.models import User
from config.asgi import application


@pytest.mark.asyncio
@pytest.mark.django_db(transaction=True)
async def test_chat_broadcast_between_members():
    owner = await database_sync_to_async(User.objects.create_user)(
        username="owner", password="pass1234", email="owner@example.com"
    )
    guest = await database_sync_to_async(User.objects.create_user)(
        username="guest", password="pass1234", email="guest@example.com"
    )
    room = await database_sync_to_async(services.create_room)(owner=owner, name="实时房间", max_players=4)
    await database_sync_to_async(services.join_room)(room=room, user=guest)

    owner_token = str(AccessToken.for_user(owner))
    guest_token = str(AccessToken.for_user(guest))

    communicator_owner = WebsocketCommunicator(
        application,
        f"/ws/rooms/{room.id}/?token={owner_token}",
        headers=[(b"origin", b"http://testserver")],
    )
    connected, _ = await communicator_owner.connect()
    assert connected
    await communicator_owner.receive_json_from()  # initial sync

    communicator_guest = WebsocketCommunicator(
        application,
        f"/ws/rooms/{room.id}/?token={guest_token}",
        headers=[(b"origin", b"http://testserver")],
    )
    connected_guest, _ = await communicator_guest.connect()
    assert connected_guest
    await communicator_guest.receive_json_from()

    await communicator_owner.send_json_to({"type": "chat.message", "payload": {"content": "大家好"}})
    owner_echo = await communicator_owner.receive_json_from()
    guest_message = await communicator_guest.receive_json_from()

    assert owner_echo["type"] == "chat.message"
    assert guest_message["payload"]["content"] == "大家好"
    assert guest_message["payload"]["sender"]["username"] == "owner"

    await communicator_owner.disconnect()
    await communicator_guest.disconnect()


@pytest.mark.asyncio
@pytest.mark.django_db(transaction=True)
async def test_ws_rejects_non_member():
    owner = await database_sync_to_async(User.objects.create_user)(
        username="owner", password="pass1234", email="owner@example.com"
    )
    outsider = await database_sync_to_async(User.objects.create_user)(
        username="outsider", password="pass1234", email="out@example.com"
    )
    room = await database_sync_to_async(services.create_room)(owner=owner, name="封闭房间", max_players=4)

    outsider_token = str(AccessToken.for_user(outsider))

    communicator = WebsocketCommunicator(
        application,
        f"/ws/rooms/{room.id}/?token={outsider_token}",
        headers=[(b"origin", b"http://testserver")],
    )
    connected, subprotocol = await communicator.connect()
    assert not connected
    assert subprotocol == 4003
