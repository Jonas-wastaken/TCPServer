# TCP Client

This module provides a Python-based TCP client for connecting to the server component of this project, sending periodic `ping` messages, receiving responses, and handling connection errors. It is designed to work with the server defined in the [`tcp-server`](../tcp-server) module.

## Features

- Connects to a configurable TCP server (host and port via environment variables)
- Sends periodic `ping` messages and prints server responses
- Handles connection, transmission, and reception errors with custom exceptions
- Supports configurable runtime duration (run for N seconds or indefinitely)
- Dockerized for easy deployment and testing

## Usage

### Prerequisites

- Python 3.13 (or compatible)
- [Docker](https://www.docker.com/) (optional, for containerized usage)

### Environment Variables

- `SERVER_HOST`: Hostname or IP address of the server (default: `localhost`)
- `SERVER_PORT`: Port number of the server (default: `12345`)

### Running Locally

```sh
python3 client.py --runtime 10
```

- Use `--runtime N` or `-r N` to specify how many seconds to run (default: 10).
- Use `--runtime 0` or a negative value to run indefinitely.

```sh
python3 client.py -r -1
```

### Running with Docker

Build the Docker image:

```sh
docker build -t tcp-client .
```

Run the container (adjust environment variables as needed):

```sh
docker run --rm -e SERVER_HOST={HOST} -e SERVER_PORT={PORT} tcp-client python3 client.py -r 10
```

Or for indefinite runtime:

```sh
docker run --rm -e SERVER_HOST={HOST} -e SERVER_PORT={PORT} tcp-client python3 client.py -r -1
```

### Running with Docker Compose

You can use Docker Compose to run the client and server together as defined in the root [`docker-compose.yaml`](../docker-compose.yaml).

Start the stack:

```sh
docker compose up --build
```

This will build and start the server, the TCP client, and any other services defined in the stack. The TCP client will connect to the server using the environment variables set in the compose file.

Use `docker compose run` to execute the python script:

```sh
docker compose run --rm tcp-client python3 client.py -r -1
```

You can also attach to the running client container and run commands interactively:

```sh
docker compose exec tcp-client sh
```

### Use Telnet

The container comes with telnet for interactive client-server communication"

```sh
telnet {host} {port}
```

## File Structure

- [`client.py`](client.py): Main TCP client implementation.
- [`Dockerfile`](Dockerfile): Docker build instructions.

## Example Output

```text
>> ping
<< Echo: ping
>> ping
<< Echo: ping
...
>> .quit
<< Goodbye!
```
