FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y build-essential default-libmysqlclient-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements/ /tmp/requirements/
RUN pip install --upgrade pip \
    && pip install -r /tmp/requirements/base.txt

COPY . /app

CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]
