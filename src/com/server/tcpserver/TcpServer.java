package com.server.tcpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A simple TCP server that listens on a port and
 * spawns a new thread for every connected client.
 */
public class TcpServer {

    // You can change the port if needed.
    private static final int PORT = 12345;

    public static void main(String[] args) {
        // Create a ServerSocket in try-with-resources so it closes automatically on exit.
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            // Main loop: accept new connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " +
                        clientSocket.getRemoteSocketAddress());

                // Eagerly spawn a new thread to handle this client
                Thread handlerThread = new Thread(new ClientHandler(clientSocket));
                handlerThread.start();
            }

        } catch (IOException e) {
            System.err.println("Error starting server on port " + PORT);
            e.printStackTrace();
        }
    }
}
