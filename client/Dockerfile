FROM python:3.13-alpine

WORKDIR /app

COPY . /app/

RUN apk add --no-cache busybox-extras

# ENTRYPOINT ["python3", "client.py"]