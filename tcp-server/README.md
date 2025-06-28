# TCP Server

This module provides a Java-based TCP server that accepts multiple client connections, manages them using a dynamically scaling thread pool, and exposes a REST API for real-time server monitoring. The server is designed to work with the [`tcp-client`](../tcp-client) and [`http-client`](../http-client) modules in this project.

## Features

- Listens for TCP client connections on a configurable port
- Dynamically scales a warm thread pool to handle client load efficiently
- Supports client connection timeouts and queueing
- Graceful shutdown via console command (`shutdown`)
- Exposes a REST API endpoint (`/monitor`) for live server statistics (active threads, pool size, queue size, connected clients)
- All settings configurable via a JSON config file
- Dockerized for easy deployment and integration with the full stack

## Usage

### Prerequisites

- Java 21 (Eclipse Temurin recommended)
- [Maven](https://maven.apache.org/) for building
- [Docker](https://www.docker.com/) (optional, for containerized usage)

### Configuration

Edit [`src/main/resources/config.json`](src/main/resources/config.json) to set server ports, thread pool size, queue size, and timeouts:

```json
{
    "port": 12345,
    "monitoring_port": 8081,
    "buffer_size": 1,
    "max_pool_size": 3,
    "client_timeout": 5000,
    "queue_size": 1
}
```

### Building Locally

Build the server JAR with Maven:

```sh
mvn clean package -DskipTests
```

### Running Locally

Run the server:

```sh
java -jar target/server-1.0.jar
```

- The server listens for TCP clients on the configured `port` (default: 12345).
- The REST monitoring endpoint is available at `http://localhost:8081/monitor` by default.

### Running with Docker

Build the Docker image:

```sh
docker build -t tcp-server .
```

Run the container:

```sh
docker run --rm -p 12345:12345 -p 8081:8081 tcp-server
```

### Running with Docker Compose

You can use Docker Compose to run the server and clients together as defined in the root [`docker-compose.yaml`](../docker-compose.yaml):

```sh
docker compose up --build
```

This will build and start the server, the TCP client, the HTTP client, and any other services defined in the stack.

## REST Monitoring Endpoint

The server exposes a REST endpoint for monitoring at:

```text
GET /monitor
```

Example response:

```json
{
  "active_threads": 1,
  "pool_size": 3,
  "queue_size": 0,
  "connected_clients": 2
}
```

## File Structure

- [`src/main/java/com/server/Server.java`](src/main/java/com/server/Server.java): Main server logic
- [`src/main/java/com/server/MonitorHandler.java`](src/main/java/com/server/MonitorHandler.java): REST API handler
- [`src/main/java/com/server/ClientHandler.java`](src/main/java/com/server/ClientHandler.java): TCP client handler
- [`src/main/java/com/server/ServerConfig.java`](src/main/java/com/server/ServerConfig.java): Configuration loader
- [`src/main/resources/config.json`](src/main/resources/config.json): Server configuration
- [`Dockerfile`](Dockerfile): Docker build instructions
- [`pom.xml`](pom.xml): Maven build file

## Example Output

```text
INFO: Server listening on port 12345
INFO: REST monitor endpoint started on http://localhost:8081/monitor
INFO: New connection from /172.18.0.2:54321
INFO: Client requested to close connection: /172.18.0.2:54321
```
