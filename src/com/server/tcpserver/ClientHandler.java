package com.server.tcpserver;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            String receivedLine;

            while ((receivedLine = in.readLine()) != null) {
                System.out.println("Received from " +
                        clientSocket.getRemoteSocketAddress() +
                        ": " + receivedLine);

                // If client says “quit”, we break out and close
                if (receivedLine.trim().equalsIgnoreCase("quit")) {
                    out.write("Goodbye!");
                    out.newLine();
                    out.flush();
                    System.out.println("Client requested to close connection: " +
                            clientSocket.getRemoteSocketAddress());
                    break;
                }

                // Otherwise, echo as before
                String response = "Echo: " + receivedLine;
                out.write(response);
                out.newLine();
                out.flush();
            }

        } catch (IOException e) {
            System.err.println("Error communicating with client " +
                    clientSocket.getRemoteSocketAddress());
            e.printStackTrace();
        } finally {
            // Closing the socket will also close its streams
            try {
                clientSocket.close();
                System.out.println("Connection closed: " +
                        clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                System.err.println("Failed to close client socket");
                e.printStackTrace();
            }
        }
    }
}
