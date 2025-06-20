package com.server.tcpserver;

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
  private final int bufferSize;
  private final int maxPoolSize;
  private final int clientTimeout;
  private final int queueSize;

  /**
   * Constructs a new ServerConfig.
   *
   * @param port        the port number to listen on
   * @param bufferSize  the number of idle threads to keep in the pool
   * @param maxPoolSize the maximum number of threads in the pool
   */
  public ServerConfig(
    int port,
    int bufferSize,
    int maxPoolSize,
    int clientTimeout,
    int queueSize
  ) {
    this.port = port;
    this.bufferSize = bufferSize;
    this.maxPoolSize = maxPoolSize;
    this.clientTimeout = clientTimeout;
    this.queueSize = queueSize;
  }

  public int getPort() {
    return port;
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
      int bufferSize = json.getInt("buffer_size");
      int maxPoolSize = json.getInt("max_pool_size");
      int clientTimeout = json.getInt("client_timeout");
      int queueSize = json.getInt("queue_size");
      return new ServerConfig(
        port,
        bufferSize,
        maxPoolSize,
        clientTimeout,
        queueSize
      );
    }
  }
}
