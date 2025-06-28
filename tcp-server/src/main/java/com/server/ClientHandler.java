package com.server;

import java.io.*;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles communication with a single TCP client.
 * Echos messages back to client.
 * Logs client events.
 */
public class ClientHandler implements Runnable {

  // Socket instance the client is connected to.
  private final Socket clientSocket;

  // Client timeout
  private final int clientTimeout;

  // Logger instance for client handler events.
  private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());

  // Counter for connected clients. Requires thread safety.
  private final AtomicInteger connectedClients;

  // Set of currently active client sockets. Requires thread safety.
  private final Set<Socket> activeClientSockets;

  /**
   * Constructs a new ClientHandler for the given client socket.
   *
   * @param socket           the client socket
   * @param connectedClients currently connected clients
   * @param clientTimeout    client timeout
   */
  public ClientHandler(Socket socket, AtomicInteger connectedClients, int clientTimeout,
      Set<Socket> activeClientSockets) {
    this.clientSocket = socket;
    this.connectedClients = connectedClients;
    this.clientTimeout = clientTimeout;
    this.activeClientSockets = activeClientSockets;
  }

  /**
   * Handles the client connection.
   */
  @Override
  public void run() {
    if (activeClientSockets != null) {
      activeClientSockets.add(clientSocket);
    }
    sendConnectedNotification();
    setClientTimeout();
    try (
        BufferedReader in = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream()));
        BufferedWriter out = new BufferedWriter(
            new OutputStreamWriter(clientSocket.getOutputStream()))) {
      handleClient(in, out);
    } catch (java.net.SocketTimeoutException e) {
      logClientTimeout();
      sendTimeoutDisconnectNotification();
    } catch (IOException e) {
      logCommunicationError("run()", e);
    } finally {
      closeClientSocket();
      updateMonitoring();
    }
  }

  /**
   * Processes client input and sends responses.
   *
   * @param in  the input reader
   * @param out the output writer
   * @throws IOException if an I/O error occurs
   */
  private void handleClient(BufferedReader in, BufferedWriter out)
      throws IOException {
    String receivedLine;
    while ((receivedLine = in.readLine()) != null) {
      if (receivedLine.trim().equalsIgnoreCase(".quit")) {
        out.write("Goodbye!");
        out.newLine();
        out.flush();
        logClientConnectionClose();
        break;
      }
      String response = "Echo: " + receivedLine;
      out.write(response);
      out.newLine();
      out.flush();
    }
  }

  /**
   * Logs a client connection request event.
   */
  private void logClientConnectionClose() {
    logger.log(
        Level.INFO,
        "Client requested to close connection: {0}",
        clientSocket.getRemoteSocketAddress());
  }

  /**
   * Logs a client timeout event.
   */
  private void logClientTimeout() {
    logger.log(
        Level.INFO,
        "Client timed out due to inactivity: {0}",
        clientSocket.getRemoteSocketAddress());
  }

  /**
   * Notifies the client it was successfully connected.
   */
  private void sendConnectedNotification() {
    try {
      BufferedWriter out = new BufferedWriter(
          new OutputStreamWriter(clientSocket.getOutputStream()));
      out.write("You are now connected.");
      out.newLine();
      out.flush();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Failed to notify client of connection", e);
    }
  }

  /**
   * Sets the client timeout.
   */
  private void setClientTimeout() {
    try {
      clientSocket.setSoTimeout(clientTimeout);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Failed to set socket timeout", e);
    }
  }

  /**
   * Sends a timeout disconnect notification to the client.
   */
  private void sendTimeoutDisconnectNotification() {
    try (
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
      out.write("Disconnected due to inactivity.");
      out.newLine();
      out.flush();
    } catch (IOException ignored) {
      // Ignored because the client is already being disconnected due to inactivity
    }
  }

  /**
   * Logs an IOException event.
   * 
   * @param e error message
   */
  private void logCommunicationError(String trace, IOException e) {
    logger.log(
        Level.SEVERE,
        "IOException in: {0}\nError communicating with client: {1}\nTraceback: {2}",
        new Object[] { trace, clientSocket.getRemoteSocketAddress(), e });
  }

  /**
   * Closes the client socket and logs the closure.
   */
  private void closeClientSocket() {
    if (clientSocket != null && !clientSocket.isClosed()) {
      try {
        clientSocket.close();
        logger.log(
            Level.INFO,
            "Connection closed: {0}",
            clientSocket.getRemoteSocketAddress());
      } catch (IOException e) {
        logCommunicationError("closeClientSocket()", e);
      }
    }
  }

  /**
   * Updates monitoring statistics.
   */
  private void updateMonitoring() {
    if (activeClientSockets != null) {
      activeClientSockets.remove(clientSocket);
    }
    if (connectedClients != null) {
      connectedClients.decrementAndGet();
    }
  }
}
