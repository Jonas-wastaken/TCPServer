"""
client.py

This module provides the HTTPClient class for communicating with a monitoring server
via HTTP. It retrieves JSON-formatted monitoring data from a configurable endpoint,
allowing other components to access the latest monitoring information.

Environment Variables:
    SERVER_HOST (str): Hostname of the monitoring server (default: 'localhost').
    MONITORING_PORT (str/int): Port number of the monitoring server (default: 8081).

Classes:
    HTTPClient: Handles HTTP requests to the monitoring server and provides access
                to the retrieved monitoring data.
"""

import os
import time
from typing import Dict
import requests


class HTTPClient:
    """
    HTTPClient is responsible for communicating with a monitoring server via HTTP.

    This class initializes a connection to a predefined monitoring endpoint,
    retrieves JSON-formatted monitoring data, and provides access to this data.
    The host and port can be configured via the SERVER_HOST and MONITORING_PORT env variables.

    Attributes:
        HOST (str): The hostname of the monitoring server.
        PORT (int): The port number of the monitoring server.
        PATH (str): The endpoint path for monitoring data.

    Methods:
        get_data: Returns the latest monitoring data as a dictionary.
        main: Retrieves and prints data from the server every 5 seconds indefinitely.
    """

    HOST = os.getenv("SERVER_HOST", "localhost")
    PORT = int(os.getenv("MONITORING_PORT", "8081"))
    PATH = "monitor"

    def __init__(self) -> None:
        """
        Initializes the HTTPClient instance.
        """

    def get_data(self) -> Dict[str, int] | None:
        """Sends GET request to the monitoring endpoint.

        Returns:
            Dict[str, int] | None: Response from the monitoring endpoint. None on Exception.
        """
        try:
            r = requests.get(
                url=f"http://{HTTPClient.HOST}:{HTTPClient.PORT}/{HTTPClient.PATH}",
                timeout=5,
            )

            return r.json()

        except requests.RequestException as e:
            print(e)

            return None

    def main(self) -> None:
        """Retrieves and prints data from the server every 5 seconds indefinitely."""
        while True:
            print(
                f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}: {self.get_data()}"
            )
            time.sleep(5)


if __name__ == "__main__":
    http_client = HTTPClient()
    http_client.main()
