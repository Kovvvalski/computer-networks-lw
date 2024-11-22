package by.dlstudio.server.httpmethodhandler.impl;

import by.dlstudio.server.httpmethodhandler.HttpMethodHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import static by.dlstudio.server.RequestHandler.STATUS_OK;

public class HttpMethodPostHandler implements HttpMethodHandler {
    private static final Logger logger = LoggerFactory.getLogger(HttpMethodPostHandler.class);

    @Override
    public void handle(String path, BufferedReader in, PrintWriter out, Map<String, String> defaultHeaders,
                       Socket clientSocket) throws IOException {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            body.append(line).append("\n");
        }

        logger.info("Received POST body: {}", body);

        out.println("HTTP/1.1 " + STATUS_OK + " OK");
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.println("POST request successfully received.");
        out.flush();
    }
}
