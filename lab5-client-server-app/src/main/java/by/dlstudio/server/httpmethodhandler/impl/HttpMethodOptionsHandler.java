package by.dlstudio.server.httpmethodhandler.impl;

import by.dlstudio.server.httpmethodhandler.HttpMethodHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import static by.dlstudio.server.RequestHandler.STATUS_NO_CONTENT;

public class HttpMethodOptionsHandler implements HttpMethodHandler {
    @Override
    public void handle(String path, BufferedReader in, PrintWriter out, Map<String, String> defaultHeaders, Socket clientSocket) throws IOException {
        out.println("HTTP/1.1 " + STATUS_NO_CONTENT + " No Content");
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.flush();
    }
}
