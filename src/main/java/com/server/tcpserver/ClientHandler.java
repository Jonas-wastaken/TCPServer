package com.server.tcpserver;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        // Create a Logger
        Logger logger = Logger.getLogger(
                ClientHandler.class.getName());

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream()))) {
            String receivedLine;

            while ((receivedLine = in.readLine()) != null) {

                // Client can end connection with ".quit" command
                if (receivedLine.trim().equalsIgnoreCase(".quit")) {
                    out.write("Goodbye!");
                    out.newLine();
                    out.flush();
                    logger.log(Level.INFO, "Client requested to close connection: {0}",
                            clientSocket.getRemoteSocketAddress());
                    break;
                }

                // Echo client messages
                String response = "Echo: " + receivedLine;
                out.write(response);
                out.newLine();
                out.flush();
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error communicating with client: {0}", clientSocket.getRemoteSocketAddress());
            e.printStackTrace();
        } finally {
            // Closing the socket will also close its streams
            try {
                clientSocket.close();
                logger.log(Level.INFO, "Connection closed: {0}", clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to close client socket");
                e.printStackTrace();
            }
        }
    }
}
