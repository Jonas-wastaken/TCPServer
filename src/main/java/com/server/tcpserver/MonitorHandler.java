package com.server.tcpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/**
 * Handler for the /monitor REST endpoint.
 * Responds with a JSON object containing server statistics.
 */
@SuppressWarnings("restriction")
public class MonitorHandler implements HttpHandler {

  private final ThreadPoolExecutor executor;
  private final AtomicInteger connectedClients;

  /**
   * Constructs a MonitorHandler with the given executor and client counter.
   *
   * @param executor the thread pool executor to monitor
   * @param connectedClients the atomic integer tracking connected clients
   */
  public MonitorHandler(
    ThreadPoolExecutor executor,
    AtomicInteger connectedClients
  ) {
    this.executor = executor;
    this.connectedClients = connectedClients;
  }

  /**
   * Handles HTTP GET requests to the /monitor endpoint.
   *
   * @param exchange the HTTP exchange object
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
      exchange.sendResponseHeaders(405, -1); // Method Not Allowed
      return;
    }
    JSONObject json = new JSONObject();
    json.put("active_threads", executor.getActiveCount());
    json.put("pool_size", executor.getPoolSize());
    json.put("queue_size", executor.getQueue().size());
    json.put("connected_clients", connectedClients.get());

    byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response);
    }
  }
}
