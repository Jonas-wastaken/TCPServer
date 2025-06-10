package com.server.tcpserver;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles communication with a single TCP client.
 */
public class ClientHandler implements Runnable {

    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final Socket clientSocket;

    /**
     * Constructs a new ClientHandler for the given client socket.
     *
     * @param socket the client socket
     */
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    /**
     * Handles the client connection.
     */
    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            handleClient(in, out);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error communicating with client: {0}", clientSocket.getRemoteSocketAddress());
            logger.log(Level.SEVERE, "Exception: ", e);
        } finally {
            closeClientSocket();
        }
    }

    /**
     * Processes client input and sends responses.
     *
     * @param in  the input reader
     * @param out the output writer
     * @throws IOException if an I/O error occurs
     */
    private void handleClient(BufferedReader in, BufferedWriter out) throws IOException {
        String receivedLine;
        while ((receivedLine = in.readLine()) != null) {
            if (receivedLine.trim().equalsIgnoreCase(".quit")) {
                out.write("Goodbye!");
                out.newLine();
                out.flush();
                logger.log(Level.INFO, "Client requested to close connection: {0}", clientSocket.getRemoteSocketAddress());
                break;
            }
            String response = "Echo: " + receivedLine;
            out.write(response);
            out.newLine();
            out.flush();
        }
    }

    /**
     * Closes the client socket and logs the closure.
     */
    private void closeClientSocket() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
                logger.log(Level.INFO, "Connection closed: {0}", clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to close client socket", e);
            }
        }
    }
}