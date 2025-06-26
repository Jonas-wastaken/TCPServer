"""
TCP Client Module

This module provides a Client class for establishing a TCP connection to a server,
sending periodic 'ping' messages, receiving responses, and handling connection errors.
It also defines custom exceptions for transmission (TXError) and reception (RXError) errors.

Example usage:
    Client(run_time=None)

Classes:
    Client: Handles TCP communication with the server.
    TXError: Exception for transmission errors.
    RXError: Exception for reception errors.
"""

import logging
import os
import socket
import time
from typing import List, TextIO


class Client:
    """
    TCP client for connecting to a server, sending periodic 'ping' messages, and handling responses.

    This class establishes a TCP connection to a specified server, sends periodic 'ping' messages,
    receives and prints responses.
    It provides methods for connecting, sending messages, receiving responses, and disconnecting.

    Attributes:
        HOST (str): The Server host address.
        PORT (int): The port the server listens on.
        __run_time (int | None): Duration in seconds to run the client. If None, runs indefinitely.
        __sock (socket.socket): TCP socket for communication.
        __in_file (TextIO): File-like object for reading from the server.
        __out_file (TextIO): File-like object for writing to the server.

    ## Methods:
        **__connect()**
        Establishes a TCP connection and prepares file-like objects. <br>
        **__get_response()**
        Reads and prints a response from the server. <br>
        **__send_message(message)**
        Sends a message to the server and prints it. <br>
        **__ping()**
        Periodically sends 'ping' messages and prints responses. <br>
        **__disconnect()**
        Sends a quit message and closes the connection. <br>
    """

    HOST = os.getenv("SERVER_HOST", "localhost")
    PORT = int(os.getenv("SERVER_PORT", "12345"))

    def __init__(self, run_time: int | None = None):
        """
        Initialize a new Client, connect to the server, send periodic pings, and disconnect.

        Args:
            run_time (int | None, optional): Runtime of the client (seconds).
                If None, runs indefinitely.
        """
        self.__run_time = run_time
        self.__sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.__in_file = TextIO()
        self.__out_file = TextIO()

        self.__connect()

        self.__ping()

        self.__disconnect()

    def __connect(self) -> None:
        """
        Establish a TCP connection to the server and prepare file-like objects for communication.
        Receives and prints the initial server responses.
        """
        self.__sock.connect((Client.HOST, Client.PORT))

        self.__in_file = self.__sock.makefile("r")
        self.__out_file = self.__sock.makefile("w")

        self.__get_response()
        self.__get_response()

    def __get_response(self) -> List[str]:
        """
        Read a response line from the server, split it into words, and print it.

        Returns:
            List[str]: The response split into a list of strings.

        Raises:
            RXError: If an exception occurs while reading from the server.
        """
        try:
            response = self.__in_file.readline().split()

            while not response:
                response = self.__in_file.readline().split()
                time.sleep(1)

            print(f"<< {" ".join(response)}")

            return response

        except Exception as e:
            logging.log(level=30, msg=e)

            raise RXError(message=e) from e

    def __send_message(self, message: str) -> str:
        """
        Send a message to the server and print it.

        Args:
            message (str): The message to send.

        Returns:
            str: The message sent.

        Raises:
            TXError: If an exception occurs while sending a message to the server.
        """
        try:
            self.__out_file.write(f"{message}\n")
            self.__out_file.flush()

            print(f">> {message}")

            return message

        except Exception as e:
            logging.log(level=30, msg=e)

            raise TXError(message=e) from e

    def __ping(self) -> None:
        """
        Periodically send 'ping' messages to the server and print responses.
        Runs for the specified run_time or indefinitely if run_time is None.
        """
        start_time = time.time()

        while (time.time() - start_time) < (
            float(self.__run_time) if self.__run_time else float("inf")
        ):
            self.__send_message(message="ping")
            self.__get_response()
            time.sleep(1)

    def __disconnect(self) -> None:
        """
        Send a '.quit' message to the server, receive the final response, and close the socket.
        """
        self.__send_message(message=".quit")
        self.__get_response()
        self.__sock.detach()

    def __str__(self) -> str:
        """
        Return a string representation of the Client instance.

        Returns:
            str: String representation of the client.
        """
        return f"Client(host={Client.HOST}, port={Client.PORT}, run_time={self.__run_time})"


class TXError(Exception):
    """Exception raised for errors that occur while sending messages to the server.

    Attributes:
        message (str): Explanation of the error.
    """

    PREAMBLE = "Error while sending a message to the server: \n"

    def __init__(self, message):
        """
        Initialize a TXError with a detailed error message.

        Args:
            message (str): The error message describing what went wrong during transmission.
        """
        self.message = TXError.PREAMBLE + message
        super().__init__(self.message)

    def __str__(self):
        """
        Return a string representation of the TXError instance.

        Returns:
            str: String representation of the error.
        """
        return f"{self.message}"


class RXError(Exception):
    """Exception raised for errors that occur while receiving messages from the server.

    Attributes:
        message (str): Explanation of the error.
    """

    PREAMBLE = "Error while reading a message from the server: \n"

    def __init__(self, message):
        """
        Initialize an RXError with a detailed error message.

        Args:
            message (str): The original error message describing what went wrong during reception.
        """
        self.message = RXError.PREAMBLE + message
        super().__init__(self.message)

    def __str__(self):
        """
        Return a string representation of the RXError instance.

        Returns:
            str: String representation of the error.
        """
        return f"{self.message}"


if __name__ == "__main__":
    RUNTIME = 10
    Client(run_time=RUNTIME)
