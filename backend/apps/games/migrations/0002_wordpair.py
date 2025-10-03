"""Create word pair model for Undercover."""

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("games", "0001_initial"),
    ]

    operations = [
        migrations.CreateModel(
            name="WordPair",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("topic", models.CharField(blank=True, max_length=120)),
                ("civilian_word", models.CharField(max_length=60)),
                ("undercover_word", models.CharField(max_length=60)),
                (
                    "difficulty",
                    models.CharField(
                        choices=[("easy", "简单"), ("medium", "适中"), ("hard", "困难")],
                        default="easy",
                        max_length=12,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
            ],
            options={
                "verbose_name": "词库词对",
                "verbose_name_plural": "词库词对",
                "ordering": ("-created_at",),
            },
        ),
    ]
