"""Add engine metadata fields to GameSession."""

from django.db import migrations, models
from django.utils import timezone


class Migration(migrations.Migration):

    dependencies = [
        ("gamecore", "0002_initial"),
    ]

    operations = [
        migrations.RenameField(
            model_name="gamesession",
            old_name="created_at",
            new_name="started_at",
        ),
        migrations.AddField(
            model_name="gamesession",
            name="current_phase",
            field=models.CharField(default="preparing", max_length=32),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="current_player_id",
            field=models.PositiveIntegerField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="ended_at",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="engine",
            field=models.CharField(default="undercover", max_length=64),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name="gamesession",
            name="round_number",
            field=models.PositiveIntegerField(default=1),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="state",
            field=models.JSONField(blank=True, default=dict),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="status",
            field=models.CharField(
                choices=[("active", "进行中"), ("completed", "已结束")],
                default="active",
                max_length=16,
            ),
        ),
        migrations.AddField(
            model_name="gamesession",
            name="updated_at",
            field=models.DateTimeField(auto_now=True, default=timezone.now),
            preserve_default=False,
        ),
    ]
