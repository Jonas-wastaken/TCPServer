package com.server;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TCP server listening for incoming client connections.
 * Clients are assigned a thread from a dynamically scaling warm thread pool.
 * Supports client timeout.
 * Can be gracefully shutdown. Active clients are forcefully disconnected.
 * Provides a REST endpoint for retrieving monitoring statistics.
 * Server settings can be configured configured via a configuration file.
 */
@SuppressWarnings("restriction") // Suppress warnings for using com.sun.net.httpserver.HttpServer
public class Server {

  // ServerConfig instance holding configuration settings.
  private final ServerConfig config;

  // Flag indicating whether the server is running.
  private volatile boolean running = true;

  // Reference to the server socket. Requires thread safety.
  private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>(null);

  // ThreadPoolExecutor instance for handling client connections.
  private final ThreadPoolExecutor executor;

  // Thread responsible for shrinking the thread pool.
  private Thread shrinker;

  // Logger instance for server events.
  private final Logger logger = Logger.getLogger(Server.class.getName());

  // HTTP server providing the RESTApi monitoring endpoint.
  private HttpServer httpServer;

  // Counter for connected clients. Requires thread safety.
  private final AtomicInteger connectedClients = new AtomicInteger(0);

  // Set of currently active client sockets. Requires thread safety.
  private final Set<Socket> activeClientSockets = ConcurrentHashMap.newKeySet();

  /**
   * Constructs a new Server with the specified configuration.
   *
   * @param config the server configuration
   */
  public Server(ServerConfig config) {
    this.config = config;
    this.executor = new ThreadPoolExecutor(
        config.getBufferSize(),
        config.getMaxPoolSize(),
        30L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(config.getQueueSize()),
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy());
    this.executor.prestartAllCoreThreads();
  }

  /**
   * Starts the server, accepting client connections and handling shutdown.
   */
  public void start() {
    startPoolShrinker();
    startShutdownWatcher();
    startRestMonitor();

    try (ServerSocket sock = new ServerSocket(config.getPort())) {
      serverSocket.set(sock);
      logger.log(Level.INFO, "Server listening on port {0}", config.getPort());

      while (running) {
        acceptClientConnections();
      }
    } catch (IOException e) {
      logger.log(
          Level.SEVERE,
          String.format("Error starting server on port %d", config.getPort()),
          e);
    } finally {
      shutdown();
    }
  }

  /**
   * Starts the background thread responsible for shrinking the thread pool.
   */
  private void startPoolShrinker() {
    shrinker = new Thread(this::shrinkThreadPool, "PoolShrinkerThread");
    shrinker.setDaemon(true);
    shrinker.start();
  }

  /**
   * Periodically shrinks the thread pool based on current load and buffer size.
   * Runs in a background thread.
   */
  private void shrinkThreadPool() {
    while (running) {
      try {
        Thread.sleep(5_000);
        int desiredCore = Math.max(
            config.getBufferSize(),
            executor.getActiveCount() + config.getBufferSize());
        desiredCore = Math.min(desiredCore, executor.getMaximumPoolSize());
        if (desiredCore < executor.getCorePoolSize()) {
          executor.setCorePoolSize(desiredCore);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * Starts the background thread that watches for the "shutdown" console command.
   */
  private void startShutdownWatcher() {
    Thread consoleWatcher = new Thread(
        this::watchShutdown,
        "ShutdownWatcherThread");
    consoleWatcher.setDaemon(true);
    consoleWatcher.start();
  }

  /**
   * Watches for the "shutdown" command from the console and initiates graceful
   * shutdown.
   * Sends a warning to all clients.
   * Schedules forced disconnect.
   */
  private void watchShutdown() {
    try (
        BufferedReader consoleIn = new BufferedReader(
            new InputStreamReader(System.in))) {
      String line;
      while ((line = consoleIn.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("shutdown")) {
          logger.log(
              Level.INFO,
              "\"shutdown\" command received. Initiating graceful shutdown...");
          running = false;
          shrinker.interrupt();
          closeServerSocket();
          sendShutdownWarningToClients();
          new Thread(this::scheduleForceDisconnect, "ForceDisconnectThread").start();
          break;
        } else {
          logger.log(
              Level.INFO,
              "Unrecognized console command: \"{0}\". Type \"shutdown\" to stop the server.",
              line);
        }
      }
    } catch (IOException e) {
      logger.log(
          Level.SEVERE,
          "Error reading from console. Server will not shut down via console watcher.",
          e);
    }
  }

  /**
   * Closes the server socket if it is open.
   */
  private void closeServerSocket() {
    ServerSocket sock = serverSocket.get();
    if (sock != null && !sock.isClosed()) {
      try {
        sock.close();
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error closing ServerSocket in watcher", e);
      }
    }
  }

  /**
   * Sends a shutdown warning message to all connected clients.
   */
  private void sendShutdownWarningToClients() {
    logger.log(Level.INFO, "Sending shutdown warning to all connected clients...");
    for (Socket socket : activeClientSockets) {
      try {
        BufferedWriter out = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream()));
        out.write("Server is shutting down in 60 seconds. Please disconnect.");
        out.newLine();
        out.flush();
      } catch (IOException ignored) {
        // Intentionally ignored: client may have disconnected or output stream is
        // unavailable during shutdown warning.
      }
    }
  }

  /**
   * Schedules force disconnect of all clients in 60 seconds.
   */
  private void scheduleForceDisconnect() {
    try {
      Thread.sleep(60_000);
      forceCloseAllClients();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Forcefully closes all active client connections and clears the set.
   */
  private void forceCloseAllClients() {
    logger.log(Level.INFO, "Forcefully closing all active client connections...");
    for (Socket socket : activeClientSockets) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // Intentionally ignored: client may have disconnected or output stream is
        // unavailable during server busy notification.
      }
    }
    activeClientSockets.clear();
    logger.log(Level.INFO, "All client connections have been closed.");
  }

  /**
   * Starts a simple REST endpoint for monitoring server status.
   * The endpoint is available at http://localhost:%port/monitor.
   */
  private void startRestMonitor() {
    try {
      httpServer = HttpServer.create(new java.net.InetSocketAddress(config.getMonitoringPort()), 0);
      httpServer.createContext(
          "/monitor",
          new MonitorHandler(executor, connectedClients));
      httpServer.setExecutor(Executors.newSingleThreadExecutor());
      httpServer.start();
      logger.log(
          Level.INFO,
          "REST monitor endpoint started on http://localhost:{0}/monitor", config.getMonitoringPort());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Failed to start REST monitor endpoint", e);
    }
  }

  /**
   * Accepts incoming client connections.
   * Notifies about connection status.
   * Updates monitoring.
   * Runs handling routine.
   */
  private void acceptClientConnections() {
    try {
      Socket clientSocket = serverSocket.get().accept();

      sendQueueNotification(clientSocket);

      logger.log(
          Level.INFO,
          "New connection from {0}",
          clientSocket.getRemoteSocketAddress());

      connectedClients.incrementAndGet();
      activeClientSockets.add(clientSocket);

      handleIncomingClient(clientSocket);

    } catch (SocketException se) {
      if (running) {
        logger.log(
            Level.SEVERE,
            "SocketException in accept(): {0}",
            se.getMessage());
      }

    } catch (IOException e) {
      logger.log(Level.SEVERE, "I/O error while accepting connection", e);
    }
  }

  /**
   * Notifies the client it has been added to the Queue.
   */
  private void sendQueueNotification(Socket clientSocket) {
    try {
      BufferedWriter out = new BufferedWriter(
          new OutputStreamWriter(clientSocket.getOutputStream()));
      out.write("You are in the queue, please wait...");
      out.newLine();
      out.flush();
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to notify client of queue status", e);
    }
  }

  /**
   * Submits client to thread pool.
   * Disconnects client if no threads are available.
   */
  private void handleIncomingClient(Socket clientSocket) {
    try {
      executor
          .execute(new ClientHandler(clientSocket, connectedClients, config.getClientTimeout(), activeClientSockets));
      adjustThreadPool();
    } catch (java.util.concurrent.RejectedExecutionException ex) {
      Logger.getLogger(Server.class.getName()).log(
          Level.WARNING,
          "Rejected connection from {0}: server is busy (queue full)",
          clientSocket.getRemoteSocketAddress());
      sendServerBusyNotification(clientSocket);
      try {
        clientSocket.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "Failed to close client socket after rejection", e);
      }
      connectedClients.decrementAndGet();
      activeClientSockets.remove(clientSocket);
    }
  }

  /**
   * Adjusts the thread pool size based on the number of active connections.
   */
  private void adjustThreadPool() {
    int currentActive = connectedClients.get();
    int desiredCore = Math.min(
        currentActive + config.getBufferSize(),
        executor.getMaximumPoolSize());
    if (desiredCore > executor.getCorePoolSize()) {
      executor.setCorePoolSize(desiredCore);
      executor.prestartAllCoreThreads();
    }
  }

  /**
   * Sends a notification if the server is busy and can't handle any more
   * connections.
   */
  private void sendServerBusyNotification(Socket clientSocket) {
    try (BufferedWriter out = new BufferedWriter(
        new OutputStreamWriter(clientSocket.getOutputStream()))) {
      out.write("Server busy. Try again later.");
      out.newLine();
      out.flush();
    } catch (IOException ignored) {
      // Intentionally ignored: client gets disconnected.
    }
  }

  /**
   * Shuts down the server and thread pool gracefully.
   * Stops the REST endpoint, monitor thread, and pool shrinker.
   * Waits for active handlers to finish, then forces shutdown if needed.
   */
  private void shutdown() {
    if (httpServer != null)
      httpServer.stop(0);
    if (shrinker != null)
      shrinker.interrupt();
    if (executor != null)
      executor.shutdown();
    logger.log(
        Level.INFO,
        "Executor shutdown initiated. Waiting for active handlers to finish...");
    try {
      if (executor != null && !executor.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.log(
            Level.WARNING,
            "Not all handlers terminated within 30 seconds. Forcing shutdown...");
        executor.shutdownNow();
      }
    } catch (InterruptedException ie) {
      logger.log(
          Level.SEVERE,
          "Interrupted while waiting for handler threads to terminate",
          ie);
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    logger.log(Level.INFO, "Server has shut down gracefully.");
  }

  /**
   * Main entry point for the server application.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    try {
      ServerConfig config = ServerConfig.loadFromResource("config.json");
      new Server(config).start();
    } catch (IOException e) {
      Logger.getLogger(Server.class.getName()).log(
          Level.SEVERE,
          "Failed to load config.json",
          e);
    }
  }
}
