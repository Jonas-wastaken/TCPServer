FROM python:3.13-alpine

WORKDIR /app

COPY . /app/

RUN pip install --no-cache-dir -r requirements.txt

ENTRYPOINT ["python3", "-u", "http_client.py"]