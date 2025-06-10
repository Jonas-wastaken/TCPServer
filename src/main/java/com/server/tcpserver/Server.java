package com.server.tcpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
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
import org.json.JSONObject;

/**
 * A TCP server that listens on a specified port, manages client connections using a warm thread pool,
 * and supports graceful shutdown via a console command. The server maintains a buffer of idle handler threads,
 * automatically scales the thread pool based on load, and allows for dynamic shrinking of the pool.
 */
public class Server {

    private final ServerConfig config;
    private final ThreadPoolExecutor executor;
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>(null);
    private final Logger logger = Logger.getLogger(Server.class.getName());
    private volatile boolean running = true;
    private Thread shrinker;

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
                new SynchronousQueue<>(),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.prestartAllCoreThreads();
    }

    /**
     * Starts the server, accepting client connections and handling shutdown.
     */
    public void start() {
        startPoolShrinker();
        startShutdownWatcher();

        try (ServerSocket sock = new ServerSocket(config.getPort())) {
            serverSocket.set(sock);
            logger.log(Level.INFO, "Server listening on port {0}", config.getPort());

            while (running) {
                acceptClientConnections();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Error starting server on port %d", config.getPort()), e);
        } finally {
            shutdown();
        }
    }

    /**
     * Accepts incoming client connections and submits them to the thread pool.
     */
    private void acceptClientConnections() {
        try {
            Socket clientSocket = serverSocket.get().accept();
            logger.log(Level.INFO, "New connection from {0}", clientSocket.getRemoteSocketAddress());
            executor.execute(new ClientHandler(clientSocket));
            adjustThreadPool();
        } catch (SocketException se) {
            if (running) {
                logger.log(Level.SEVERE, "SocketException in accept(): {0}", se.getMessage());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "I/O error while accepting connection", e);
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
     */
    private void shrinkThreadPool() {
        while (running) {
            try {
                Thread.sleep(10_000);
                int desiredCore = Math.max(config.getBufferSize(), executor.getActiveCount() + config.getBufferSize());
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
     * Adjusts the thread pool size based on the number of active connections.
     */
    private void adjustThreadPool() {
        int currentActive = executor.getActiveCount();
        int desiredCore = Math.min(currentActive + config.getBufferSize(), executor.getMaximumPoolSize());
        if (desiredCore > executor.getCorePoolSize()) {
            executor.setCorePoolSize(desiredCore);
            executor.prestartAllCoreThreads();
        }
    }

    /**
     * Starts the background thread that watches for the "shutdown" console command.
     */
    private void startShutdownWatcher() {
        Thread consoleWatcher = new Thread(this::watchShutdown, "ShutdownWatcherThread");
        consoleWatcher.setDaemon(true);
        consoleWatcher.start();
    }

    /**
     * Watches for the "shutdown" command from the console and initiates graceful shutdown.
     */
    private void watchShutdown() {
        try (BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = consoleIn.readLine()) != null) {
                if (line.trim().equalsIgnoreCase("shutdown")) {
                    logger.log(Level.INFO, "\"shutdown\" command received. Initiating graceful shutdown...");
                    running = false;
                    shrinker.interrupt();
                    closeServerSocket();
                    break;
                } else {
                    logger.log(Level.INFO, "Unrecognized console command: \"{0}\". Type \"shutdown\" to stop the server.", line);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from console. Server will not shut down via console watcher.", e);
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
     * Shuts down the server and thread pool gracefully.
     */
    private void shutdown() {
        if (shrinker != null) shrinker.interrupt();
        if (executor != null) executor.shutdown();
        logger.log(Level.INFO, "Executor shutdown initiated. Waiting for active handlers to finish...");
        try {
            if (executor != null && !executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Not all handlers terminated within 30 seconds. Forcing shutdown...");
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            logger.log(Level.SEVERE, "Interrupted while waiting for handler threads to terminate", ie);
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
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "Failed to load config.json", e);
        }
    }
}

/**
 * Encapsulates server configuration loaded from a JSON file.
 */
class ServerConfig {
    private final int port;
    private final int bufferSize;
    private final int maxPoolSize;

    /**
     * Constructs a new ServerConfig.
     *
     * @param port        the port number to listen on
     * @param bufferSize  the number of idle threads to keep in the pool
     * @param maxPoolSize the maximum number of threads in the pool
     */
    private ServerConfig(int port, int bufferSize, int maxPoolSize) {
        this.port = port;
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * Gets the port number.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the buffer size (number of idle threads).
     *
     * @return the buffer size
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Gets the maximum pool size.
     *
     * @return the maximum pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Loads server configuration from a JSON resource on the classpath.
     *
     * @param resourceName the resource name (e.g., "config.json")
     * @return a ServerConfig instance
     * @throws IOException if the resource cannot be read or parsed
     */
    public static ServerConfig loadFromResource(String resourceName) throws IOException {
        try (
            InputStream is = ServerConfig.class.getClassLoader().getResourceAsStream(resourceName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is))
        ) {
            if (is == null) {
                throw new IOException(resourceName + " not found in classpath");
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            int port = json.getInt("port");
            int bufferSize = json.getInt("buffer_size");
            int maxPoolSize = json.getInt("max_pool_size");
            return new ServerConfig(port, bufferSize, maxPoolSize);
        }
    }
}