import java.io.*;
import java.net.*;

public class SimpleTcpServer {
    public static void main(String[] args) {
        int portNumber = 12345; // Example port number

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            System.out.println("Server started on port " + portNumber);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);

                // Obtain client IP address
                InetAddress clientAddress = clientSocket.getInetAddress();
                System.out.println("Client IP: " + clientAddress.getHostAddress());

                // Handle client communication in a separate thread
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                // Handle client input/output streams for data exchange
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // Process incoming data from client
                    System.out.println("Received from client: " + inputLine);

                    // Example: Echo back to client
                    out.println("Received: " + inputLine);
                }

                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
