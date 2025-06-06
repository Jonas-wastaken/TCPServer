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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TCP server that listens on a port, spawns a new thread for every connected
 * client, and keeps a “buffer” of 3 idle handler‐threads at all times. When a
 * new
 * client connects, one of those 3 immediately picks up the task, and the pool
 * automatically spawns a new thread to bring the idle count back to 3. The pool
 * also shrinks itself (down to active+3) when load drops.
 */
public class TcpServer {

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
  private static volatile ServerSocket serverSocket = null;

  public static void main(String[] args) {
    Logger logger = Logger.getLogger(TcpServer.class.getName());

    // Prestart BUFFER_SIZE core threads
    executor.prestartAllCoreThreads();

    // Launch a shrinker thread
    Thread shrinker = new Thread(() -> shrinkThreadPool(), "PoolShrinkerThread");
    shrinker.setDaemon(true);
    shrinker.start();

    // Shutdown watcher
    startConsoleWatcher(logger, shrinker);

    // Start the socket
    try {
      serverSocket = new ServerSocket(PORT);
      logger.log(Level.INFO, "Server listening on port {0}", PORT);

      while (running) {
        acceptClientConnections(logger);
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error starting server on port " + PORT, e);
    } finally {
      // Signal shrinker to stop (in case it’s sleeping)
      shrinker.interrupt();

      // Close the ServerSocket
      if (serverSocket != null && !serverSocket.isClosed()) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          logger.log(Level.SEVERE, "Failed to close ServerSocket", e);
        }
      }

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

  private static void acceptClientConnections (Logger logger) {
    try {
      Socket clientSocket = serverSocket.accept();
      logger.log(
          Level.INFO,
          "New connection from {0}",
          clientSocket.getRemoteSocketAddress());

      executor.execute(new ClientHandler(clientSocket));

      startThread();

    } catch (SocketException se) {
      // If the ServerSocket is closed from the watcher thread, accept() will throw.
      if (running) {
        // Unexpected SocketException (e.g. port forcibly closed). Log and return.
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
   * Service that checks if idle threads can be killed.
   * Calculates currently desired pool size. -> Can not exceed MAX_POOL_SIZE
   * Checks if the current threadPoolExecutor has > BUFFER_SIZE threads available.
   * Let's idle threads > BUFFER_SIZE time out.
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
   * Starts a new thread when client connects.
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
   * Launches a thread that listens on System.in for shutdown command.
   * Once detected, it will close the ServerSocket (causing accept() to throw),
   * set running = false so the main loop exits, and interrupts the shrinker.
   */
  private static void startConsoleWatcher(Logger logger, Thread shrinker) {
    Thread consoleThread = new Thread(
      () -> {
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
              if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                  serverSocket.close();
                } catch (IOException e) {
                  logger.log(
                    Level.SEVERE,
                    "Error closing ServerSocket in watcher",
                    e
                  );
                }
              }
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
      },
      "ConsoleWatcherThread"
    );

    consoleThread.setDaemon(true);
    consoleThread.start();
  }
}
