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
    retrieves JSON-formatted monitoring data, and provides access to this data
    through a property. The server host and port can be configured via the
    SERVER_HOST and MONITORING_PORT environment variables, respectively.

    Attributes:
        HOST (str): The hostname of the monitoring server.
        PORT (int): The port number of the monitoring server.
        PATH (str): The endpoint path for monitoring data.

    Methods:
        get_data (property): Returns the latest monitoring data as a dictionary.
    """

    HOST = os.getenv("SERVER_HOST", "localhost")
    PORT = int(os.getenv("MONITORING_PORT", "8081"))
    PATH = "monitor"

    def __init__(self) -> None:
        """
        Initializes the HTTPClient instance.
        Sends GET request to the monitoring endpoint.
        Fetches and stores the JSON response data for later access.
        """
        r = requests.get(
            url=f"http://{HTTPClient.HOST}:{HTTPClient.PORT}/{HTTPClient.PATH}",
            timeout=10,
        )

        self.__data = r.json()

    @property
    def get_data(self) -> Dict[str, int]:
        """_summary_

        Returns:
            Dict[str, int]: _description_
        """
        return self.__data


if __name__ == "__main__":
    while True:
        try:
            monitoring_data = HTTPClient().get_data
            print(
                f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}: {monitoring_data}"
            )
        except Exception as e:
            print(e)
        time.sleep(10)
