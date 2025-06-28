# HTTP Client

This module provides a Python-based HTTP client for communicating with the monitoring REST API exposed by the server component of this project. It retrieves JSON-formatted monitoring data from a configurable endpoint, allowing you to observe server statistics such as active threads, pool size, queue size, and connected clients. It is designed to work with the server defined in the [`tcp-server`](../tcp-server) module.

## Features

- Connects to a configurable monitoring REST API (host and port via environment variables)
- Retrieves and prints server monitoring statistics in JSON format
- Supports periodic polling for continuous monitoring
- Dockerized for easy deployment and integration with the full stack

## Usage

### Prerequisites

- Python 3.13 (or compatible)
- [Docker](https://www.docker.com/) (optional, for containerized usage)

### Environment Variables

- `SERVER_HOST`: Hostname or IP address of the monitoring server (default: `localhost`)
- `MONITORING_PORT`: Port number of the monitoring server (default: `8081`)

### Running Locally

Install dependencies:

```sh
pip install -r requirements.txt
```

Run the client:

```sh
python3 client.py
```

The client will periodically fetch and print monitoring data from the server's `/monitor` endpoint.

### Running with Docker

Build the Docker image:

```sh
docker build -t http-client .
```

Run the container (adjust environment variables as needed):

```sh
docker run --rm -e SERVER_HOST={HOST} -e MONITORING_PORT={PORT} http-client
```

### Running with Docker Compose

You can use Docker Compose to run the HTTP client and server together as defined in the root [`docker-compose.yaml`](../docker-compose.yaml).

Start the stack:

```sh
docker compose up --build
```

This will build and start the server, the HTTP client, and any other services defined in the stack. The HTTP client will connect to the monitoring endpoint using the environment variables set in the compose file.

## File Structure

- [`client.py`](client.py): Main HTTP client implementation.
- [`Dockerfile`](Dockerfile): Docker build instructions.
- [`requirements.txt`](requirements.txt): Python dependencies.

## Example Output

```text
2025-06-28 12:00:00: {'active_threads': 1, 'pool_size': 3, 'queue_size': 0, 'connected_clients': 2}
2025-06-28 12:00:10: {'active_threads': 1, 'pool_size': 3, 'queue_size': 0, 'connected_clients': 1}
...
```
