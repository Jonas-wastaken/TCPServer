# TCPServer Project

This project is a modular, container-friendly system for demonstrating scalable TCP server/client communication and real-time server monitoring. It consists of three main components:

- **TCP Server**: A Java-based, thread-pooled TCP server with a REST API for live monitoring.
- **TCP Client**: A Python client that connects to the server, sends periodic messages, and handles responses.
- **HTTP Client**: A Python client that polls the server's REST API to display real-time server statistics.

All components are designed to work together seamlessly, both locally and in Docker/Docker Compose environments.

---

## Project Structure

```text
tcp-server/    # Java TCP server with REST monitoring endpoint
tcp-client/    # Python TCP client for sending/receiving messages
http-client/   # Python HTTP client for monitoring server stats
docker-compose.yaml
```

---

## Features

- **Scalable TCP server** with dynamic thread pool and client queueing
- **Graceful shutdown** and client notification
- **Live REST monitoring** endpoint (`/monitor`) for server stats
- **Configurable** via JSON and environment variables
- **Dockerized**: Each component has a Dockerfile; full stack runs with Docker Compose

---

## Quick Start

### Prerequisites

- [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/)
- (For local builds) Java 21, Python 3.13, Maven

### Running the Full Stack

From the project root, start all services:

```sh
docker compose up --build
```

- The server listens for TCP clients on port `12345` and exposes monitoring on port `8081`.
- The TCP client connects to the server and sends periodic `ping` messages.
- The HTTP client polls the server's `/monitor` endpoint and prints stats.

---

## Individual Modules

- [`tcp-server`](tcp-server/README.md): Java TCP server with REST monitoring
- [`tcp-client`](tcp-client/README.md): Python TCP client for testing server
- [`http-client`](http-client/README.md): Python HTTP client for monitoring

Each module has its own README with detailed usage instructions.

---

## Configuration

- **Server settings**: [`tcp-server/src/main/resources/config.json`](tcp-server/src/main/resources/config.json)
- **Client/server ports**: Set via config file or environment variables (see module READMEs)

---

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3).

See the [LICENSE](https://www.gnu.org/licenses/gpl-3.0.txt) file for details.
