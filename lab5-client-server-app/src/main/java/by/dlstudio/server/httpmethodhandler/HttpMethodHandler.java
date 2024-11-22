package by.dlstudio.server.httpmethodhandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

@FunctionalInterface
public interface HttpMethodHandler {
    void handle(String path, BufferedReader in, PrintWriter out, Map<String, String> defaultHeaders,
                Socket clientSocket) throws IOException;
}