package by.dlstudio.server;

import by.dlstudio.server.httpmethodhandler.HttpMethodHandler;
import by.dlstudio.server.httpmethodhandler.impl.HttpMethodGetHandler;
import by.dlstudio.server.httpmethodhandler.impl.HttpMethodOptionsHandler;
import by.dlstudio.server.httpmethodhandler.impl.HttpMethodPostHandler;
import org.slf4j.Logger;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler implements Runnable {
    // HTTP Status Codes
    public static final int STATUS_OK = 200;
    public static final int STATUS_BAD_REQUEST = 400;
    public static final int STATUS_NOT_FOUND = 404;
    public static final int STATUS_METHOD_NOT_ALLOWED = 405;
    public static final int STATUS_NO_CONTENT = 204;

    private final Socket clientSocket;
    private final String rootDirectory;
    private final Map<String, String> defaultHeaders;
    private final Logger logger;

    private final Map<String, HttpMethodHandler> methodHandlers = new HashMap<>();

    public RequestHandler(Socket clientSocket, String rootDirectory, Map<String, String> defaultHeaders, Logger logger) {
        this.clientSocket = clientSocket;
        this.rootDirectory = rootDirectory;
        this.defaultHeaders = defaultHeaders;
        this.logger = logger;

        methodHandlers.put("GET", new HttpMethodGetHandler());
        methodHandlers.put("POST", new HttpMethodPostHandler());
        methodHandlers.put("OPTIONS", new HttpMethodOptionsHandler());
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

            logger.info("Request: {}", requestLine);

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendError(out, STATUS_BAD_REQUEST, "Bad Request");
                return;
            }

            String method = requestParts[0];
            String path = requestParts[1];
            if (!path.startsWith("/")) {
                sendError(out, STATUS_BAD_REQUEST, "Bad Request");
                return;
            }

            path = rootDirectory + path; // Resolve file path

            methodHandlers.getOrDefault(method.toUpperCase(), (p, i, o, d, c) -> sendError(
                    o, STATUS_METHOD_NOT_ALLOWED, "Method Not Allowed"
            )).handle(path, in, out, defaultHeaders, clientSocket);
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

    public static void sendError(PrintWriter out, int statusCode, String message) {
        out.println("HTTP/1.1 " + statusCode + " " + message);
        out.println("Content-Type: text/plain");
        out.println();
        out.println(message);
        out.flush();
    }
}