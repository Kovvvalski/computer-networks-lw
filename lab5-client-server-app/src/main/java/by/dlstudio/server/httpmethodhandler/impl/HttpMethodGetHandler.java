package by.dlstudio.server.httpmethodhandler.impl;

import by.dlstudio.server.RequestHandler;
import by.dlstudio.server.httpmethodhandler.HttpMethodHandler;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static by.dlstudio.server.RequestHandler.STATUS_NOT_FOUND;
import static by.dlstudio.server.RequestHandler.STATUS_OK;

public class HttpMethodGetHandler implements HttpMethodHandler {
    @Override
    public void handle(String path, BufferedReader in, PrintWriter out, Map<String, String> defaultHeaders,
                       Socket clientSocket) throws IOException {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            RequestHandler.sendError(out, STATUS_NOT_FOUND, "Not Found");
            return;
        }

        String mimeType = Files.probeContentType(Paths.get(path));
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        out.println("HTTP/1.1 " + STATUS_OK + " OK");
        out.println("Content-Type: " + mimeType);
        out.println("Content-Length: " + file.length());
        defaultHeaders.forEach((key, value) -> out.println(key + ": " + value));
        out.println();
        out.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
            }
            clientSocket.getOutputStream().flush();
        }
    }
}
