package com.server.tcpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TCP server that listens on a specified port, manages client connections using a warm thread pool,
 * and supports graceful shutdown via a console command. The server maintains a buffer of idle handler threads,
 * automatically scales the thread pool based on load, and allows for dynamic shrinking of the pool.
 * 
 * <p>
 * Features:
 * <ul>
 *   <li>Spawns a new thread for every connected client.</li>
 *   <li>Keeps a buffer of idle handler threads for fast response to new connections.</li>
 *   <li>Automatically grows and shrinks the thread pool based on active connections.</li>
 *   <li>Supports graceful shutdown via a "shutdown" command on the console.</li>
 * </ul>
 * </p>
 */
public class Server {

  // Port the server listens on
  private static final int PORT = 12345;

  // Warm Thread Pool
  private static final int BUFFER_SIZE = 3;

  // Maximum amount of client connections
  private static final int MAX_POOL_SIZE = 10;

  // The ThreadPoolExecutor that manages ClientHandler threads
  private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
    BUFFER_SIZE,
    MAX_POOL_SIZE,
    30L,
    TimeUnit.SECONDS,
    new SynchronousQueue<>(),
    Executors.defaultThreadFactory(),
    new ThreadPoolExecutor.AbortPolicy()
  );

  // Flag indicating whether the server is still running
  private static volatile boolean running = true;

  // ServerSocket instance
  private static final AtomicReference<ServerSocket> serverSocket =
    new AtomicReference<>(null);

  private static final Logger logger = Logger.getLogger(Server.class.getName());

  // PoolShrinkerThread
  private static final Thread shrinker = new Thread(
    Server::shrinkThreadPool,
    "PoolShrinkerThread"
  );

  // ShutdownWatcherThread
  private static final Thread consoleWatcher = new Thread(
    Server::watchShutdown,
    "ShutdownWatcherThread"
  );

  /**
   * The main entry point for the TCP server application.
   * Initializes the thread pool, starts background service threads,
   * and begins accepting client connections.
   */
  public static void main(String[] args) {
    // Prestart BUFFER_SIZE core threads
    executor.prestartAllCoreThreads();

    // Launch PoolShrinkerThread
    shrinker.setDaemon(true);
    shrinker.start();

    // Launch ShutdownWatcherThread
    consoleWatcher.setDaemon(true);
    consoleWatcher.start();

    // Start the socket
    try (ServerSocket sock = new ServerSocket(PORT)) {
      serverSocket.set(sock);
      logger.log(Level.INFO, "Server listening on port {0}", PORT);

      while (running) {
        acceptClientConnections();
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error starting server on port " + PORT, e);
    } finally {
      // Signal shrinker to stop (in case it’s sleeping)
      shrinker.interrupt();

      // Shut down the executor
      executor.shutdown();
      logger.log(
        Level.INFO,
        "Executor shutdown initiated. Waiting for active handlers to finish..."
      );

      // Wait for all handlers to finish (up to a timeout)
      try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
          logger.log(
            Level.WARNING,
            "Not all handlers terminated within 30 seconds. Forcing shutdown..."
          );
          executor.shutdownNow();
        }
      } catch (InterruptedException ie) {
        logger.log(
          Level.SEVERE,
          "Interrupted while waiting for handler threads to terminate",
          ie
        );
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }

      logger.log(Level.INFO, "Server has shut down gracefully.");
    }
  }

  /**
   * Accepts incoming client connections on the server socket.
   * For each connection, logs the event and submits a new ClientHandler to the thread pool.
   * Handles exceptions related to socket operations.
   */
  private static void acceptClientConnections() {
    try {
      Socket clientSocket = serverSocket.get().accept();
      logger.log(
        Level.INFO,
        "New connection from {0}",
        clientSocket.getRemoteSocketAddress()
      );

      executor.execute(new ClientHandler(clientSocket));

      startThread();
    } catch (SocketException se) {
      // If the ServerSocket is closed from the watcher thread, accept() will throw.
      if (running) {
        // Unexpected SocketException (e.g. port forcibly closed). Log and return.
        logger.log(
          Level.SEVERE,
          "SocketException in accept(): {0}",
          se.getMessage()
        );
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "I/O error while accepting connection", e);
    }
  }

  /**
   * Service thread that periodically checks if idle threads in the pool can be reduced.
   * Shrinks the core pool size to match the current load, maintaining a buffer of idle threads.
   * Exits when the server is no longer running.
   */
  private static void shrinkThreadPool() {
    while (running) {
      try {
        Thread.sleep(10_000);
        // Desired core = max(BUFFER_SIZE, active + BUFFER_SIZE), but don't exceed
        // maximum
        int desiredCore = executor.getActiveCount() + BUFFER_SIZE;
        if (desiredCore < BUFFER_SIZE) {
          desiredCore = BUFFER_SIZE;
        }
        if (desiredCore > executor.getMaximumPoolSize()) {
          desiredCore = executor.getMaximumPoolSize();
        }
        // Shrink corePoolSize
        if (desiredCore < executor.getCorePoolSize()) {
          executor.setCorePoolSize(desiredCore);
        }
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * Adjusts the thread pool to ensure a buffer of idle threads is maintained.
   * Increases the core pool size if needed and prestarts core threads.
   * Called when a new client connects.
   */
  private static void startThread() {
    // Recompute desired core = active + BUFFER_SIZE, capped at maximumPoolSize.
    int currentActive = executor.getActiveCount();
    int desiredCore = currentActive + BUFFER_SIZE;
    if (desiredCore > executor.getMaximumPoolSize()) {
      desiredCore = executor.getMaximumPoolSize();
    }
    // Increase corePoolSize if needed to maintain BUFFER_SIZE idle threads
    if (desiredCore > executor.getCorePoolSize()) {
      executor.setCorePoolSize(desiredCore);
      executor.prestartAllCoreThreads();
    }
  }

  /**
   * Launches a watcher thread that listens for the "shutdown" command on System.in.
   * When the command is received, initiates a graceful shutdown by closing the server socket,
   * interrupting the pool shrinker, and setting the running flag to false.
   */
  private static void watchShutdown() {
    try (
      BufferedReader consoleIn = new BufferedReader(
        new InputStreamReader(System.in)
      )
    ) {
      String line;
      while ((line = consoleIn.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("shutdown")) {
          logger.log(
            Level.INFO,
            "\"shutdown\" command received. Initiating graceful shutdown..."
          );
          running = false;

          // Interrupt the shrinker so it can exit promptly
          shrinker.interrupt();

          // Close the ServerSocket to unblock accept()
          closeServerSocketInWatcher();
          break;
        } else {
          logger.log(
            Level.INFO,
            "Unrecognized console command: \"{0}\". Type \"shutdown\" to stop the server.",
            line
          );
        }
      }
    } catch (IOException e) {
      logger.log(
        Level.SEVERE,
        "Error reading from console. Server will not shut down via console watcher.",
        e
      );
    }
  }

  /**
   * Closes the ServerSocket instance if it is open.
   * Used by the shutdown watcher to unblock the accept() call and allow the server to exit.
   * Logs any IOException that occurs during closure.
   */
  private static void closeServerSocketInWatcher() {
    if (serverSocket.get() != null && !serverSocket.get().isClosed()) {
      try {
        serverSocket.get().close();
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error closing ServerSocket in watcher", e);
      }
    }
  }
}
