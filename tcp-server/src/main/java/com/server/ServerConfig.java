package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.JSONObject;

/**
 * Encapsulates server configuration loaded from a JSON file.
 */
public class ServerConfig {

  private final int port;
  private final int monitoringPort;
  private final int bufferSize;
  private final int maxPoolSize;
  private final int clientTimeout;
  private final int queueSize;

  /**
   * Constructs a new ServerConfig.
   *
   * @param port            the port to listen on
   * @param monitoringPort  the port to send monitoring statistics on
   * @param bufferSize      the number of idle threads to keep in the pool
   * @param maxPoolSize     the maximum number of threads in the pool
   * @param clientTimeout   the idle timeout for client connections
   * @param queueSize       the maximum number of clients held in queue
   */
  public ServerConfig(
    int port,
    int monitoringPort,
    int bufferSize,
    int maxPoolSize,
    int clientTimeout,
    int queueSize
  ) {
    this.port = port;
    this.monitoringPort = monitoringPort;
    this.bufferSize = bufferSize;
    this.maxPoolSize = maxPoolSize;
    this.clientTimeout = clientTimeout;
    this.queueSize = queueSize;
  }

  public int getPort() {
    return port;
  }

  public int getMonitoringPort() {
    return monitoringPort;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public int getClientTimeout() {
    return clientTimeout;
  }

  public int getQueueSize() {
    return queueSize;
  }

  /**
   * Loads server configuration from a JSON resource on the classpath.
   *
   * @param resourceName the resource name (e.g., "config.json")
   * @return a ServerConfig instance
   * @throws IOException if the resource cannot be read or parsed
   */
  public static ServerConfig loadFromResource(String resourceName)
    throws IOException {
    try (
      InputStream is =
        ServerConfig.class.getClassLoader().getResourceAsStream(resourceName);
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
      int monitoringPort = json.getInt("monitoring_port");
      int bufferSize = json.getInt("buffer_size");
      int maxPoolSize = json.getInt("max_pool_size");
      int clientTimeout = json.getInt("client_timeout");
      int queueSize = json.getInt("queue_size");
      return new ServerConfig(
        port,
        monitoringPort,
        bufferSize,
        maxPoolSize,
        clientTimeout,
        queueSize
      );
    }
  }
}
