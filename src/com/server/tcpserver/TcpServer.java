package com.server.tcpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TCP server that listens on a port, spawns a new thread for every connected
 * client,
 * and supports a “shutdown” command on the server console to close everything
 * gracefully.
 */
public class TcpServer {

    // Port the server listens on
    private static final int PORT = 12345;

    // ExecutorService for client-handler threads
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // Flag indicating whether the server is still running
    private static volatile boolean running = true;

    // Move ServerSocket to a static field so the watcher can close it
    private static volatile ServerSocket serverSocket = null;

    public static void main(String[] args) {
        Logger logger = Logger.getLogger(TcpServer.class.getName());

        // Start a separate thread that watches System.in for the "shutdown" command
        startConsoleWatcher(logger);

        try {
            serverSocket = new ServerSocket(PORT);
            logger.log(Level.INFO, "Server listening on port {0}", PORT);

            while (running) {
                try {
                    // This will block until a client connects—or until serverSocket is closed.
                    Socket clientSocket = serverSocket.accept();
                    logger.log(Level.INFO, "New connection from {0}", clientSocket.getRemoteSocketAddress());

                    // Submit a new ClientHandler to the executor
                    executor.submit(new ClientHandler(clientSocket));

                } catch (SocketException se) {
                    // If the ServerSocket is closed from the watcher thread, accept() will throw a
                    // SocketException.
                    if (running) {
                        // Unexpected SocketException (e.g. port forcibly closed). Log and break.
                        logger.log(Level.SEVERE, "SocketException in accept(): {0}", se.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error starting server on port " + PORT, e);
        } finally {
            // 1. Close the ServerSocket if it’s not already closed
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to close ServerSocket", e);
                }
            }

            // 2. Shut down the executor so it stops accepting new handler tasks
            executor.shutdown();
            logger.log(Level.INFO, "Executor shutdown initiated. Waiting for active handlers to finish...");

            // 3. Wait for all handlers to finish (up to a timeout)
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "Not all handlers terminated within 60 seconds. Forcing shutdown...");
                    executor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                logger.log(Level.SEVERE, "Interrupted while waiting for handler threads to terminate", ie);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.log(Level.INFO, "Server has shut down gracefully.");
        }
    }

    /**
     * Launches a thread that listens on System.in for the exact word "shutdown".
     * Once detected, it will close the ServerSocket (causing accept() to throw)
     * and set running = false so the main loop exits.
     */
    private static void startConsoleWatcher(Logger logger) {
        Thread consoleThread = new Thread(() -> {
            try (BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = consoleIn.readLine()) != null) {
                    if (line.trim().equalsIgnoreCase("shutdown")) {
                        logger.log(Level.INFO, "\"shutdown\" command received. Initiating graceful shutdown...");
                        running = false;

                        // Close the ServerSocket to unblock accept()
                        if (serverSocket != null && !serverSocket.isClosed()) {
                            try {
                                serverSocket.close();
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, "Error closing ServerSocket in watcher", e);
                            }
                        }
                        break;
                    } else {
                        logger.log(Level.INFO,
                                "Unrecognized console command: \"{0}\". Type \"shutdown\" to stop the server.", line);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error reading from console. Server will not shut down via console watcher.",
                        e);
            }
        });

        consoleThread.setDaemon(true);
        consoleThread.setName("ConsoleWatcherThread");
        consoleThread.start();
    }
}
