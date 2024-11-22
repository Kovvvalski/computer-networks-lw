package by.dlstudio.server;

import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final String rootDirectory;
    private final Map<String, String> defaultHeaders;
    private final Logger logger;

    public RequestHandler(Socket clientSocket, String rootDirectory, Map<String, String> defaultHeaders, Logger logger) {
        this.clientSocket = clientSocket;
        this.rootDirectory = rootDirectory;
        this.defaultHeaders = defaultHeaders;
        this.logger = logger;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream())) {

            // Parse HTTP Request
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            logger.info("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendError(out, 400, "Bad Request");
                return;
            }

            String method = requestParts[0];
            String path = requestParts[1];
            if (!path.startsWith("/")) {
                sendError(out, 400, "Bad Request");
                return;
            }

            path = rootDirectory + path; // Resolve file path

            // Handle methods
            switch (method) {
                case "GET":
                    handleGet(path, out);
                    break;
                case "POST":
                    handlePost(in, out);
                    break;
                case "OPTIONS":
                    handleOptions(out);
                    break;
                default:
                    sendError(out, 405, "Method Not Allowed");
            }
        } catch (IOException e) {
            logger.error("Error handling client request: ", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket: ", e);
            }
        }
    }

    private void handleGet(String path, PrintWriter out) throws IOException {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            sendError(out, 404, "Not Found");
            return;
        }

        String mimeType = Files.probeContentType(Paths.get(path));
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        // Send Response
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: " + mimeType);
        out.println("Content-Length: " + file.length());
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.flush();

        // Send File Content
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
            }
            clientSocket.getOutputStream().flush();
        }
    }

    private void handlePost(BufferedReader in, PrintWriter out) throws IOException {
        // This implementation just reads the body and responds with a 200 OK
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            body.append(line).append("\n");
        }

        logger.info("Received POST body: " + body);

        // Send Response
        out.println("HTTP/1.1 200 OK");
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.println("POST request successfully received.");
        out.flush();
    }

    private void handleOptions(PrintWriter out) {
        out.println("HTTP/1.1 204 No Content");
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.flush();
    }

    private void sendError(PrintWriter out, int statusCode, String message) {
        out.println("HTTP/1.1 " + statusCode + " " + message);
        out.println("Content-Type: text/plain");
        out.println();
        out.println(message);
        out.flush();
    }
}