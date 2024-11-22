package by.dlstudio.client;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class HttpClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpClient.class);
    private final String host;
    private final int port;
    private final Map<String, String> defaultHeaders;
    private static final int BUFFER_SIZE = 8192;

    public HttpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "JavaHttpClient/1.0");
    }

    public static void main(String[] args) {
        Options options = createOptions();

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                printHelp(options);
                return;
            }

            String url = cmd.getOptionValue("url");
            if (url == null) {
                throw new ParseException("URL is required");
            }
            URL parsedUrl = new URL(url);

            HttpClient client = new HttpClient(
                    parsedUrl.getHost(),
                    parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort()
            );

            String outputPath = cmd.getOptionValue("output");
            if (outputPath == null) {
                String fileName = new File(parsedUrl.getPath()).getName();
                if (fileName.isEmpty()) {
                    fileName = "index.html";
                }
                outputPath = fileName;
            }

            if (cmd.hasOption("template")) {
                client.executeTemplate(cmd.getOptionValue("template"), outputPath);
                return;
            }

            String method = cmd.getOptionValue("method", "GET");
            String path = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();

            Map<String, String> headers = new HashMap<>();
            if (cmd.hasOption("header")) {
                for (String header : cmd.getOptionValues("header")) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            String body = null;
            if (cmd.hasOption("data")) {
                body = cmd.getOptionValue("data");
            } else if (cmd.hasOption("file")) {
                body = Files.readString(Paths.get(cmd.getOptionValue("file")));
            }

            client.sendRequest(method, path, headers, body, outputPath);

        } catch (ParseException e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printHelp(options);
        } catch (Exception e) {
            System.err.println("Error executing request: " + e.getMessage());
        }
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption(Option.builder("u")
                .longOpt("url")
                .hasArg()
                .desc("Target URL (required)")
                .build());

        options.addOption(Option.builder("X")
                .longOpt("method")
                .hasArg()
                .desc("HTTP method (default: GET)")
                .build());

        options.addOption(Option.builder("H")
                .longOpt("header")
                .hasArgs()
                .desc("Request header (format: 'Name: Value')")
                .build());

        options.addOption(Option.builder("d")
                .longOpt("data")
                .hasArg()
                .desc("Request body data")
                .build());

        options.addOption(Option.builder("f")
                .longOpt("file")
                .hasArg()
                .desc("File containing request body")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("template")
                .hasArg()
                .desc("Request template file")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .desc("Output file path")
                .build());

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show this help message")
                .build());

        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.printHelp("java -jar client.jar", "\nHTTP Client Options:", options,
                "\nExamples:\n" +
                        "  java -jar client.jar -u http://example.com/file.pdf -o downloaded.pdf\n" +
                        "  java -jar client.jar -X POST -u http://example.com/api/data -d '{\"key\":\"value\"}'\n" +
                        "  java -jar client.jar -u http://example.com -H \"Content-Type: application/json\" -f data.json\n" +
                        "  java -jar client.jar -t request-template.txt -o response.txt\n",
                true);
    }

    public void executeTemplate(String templatePath, String outputPath) throws IOException {
        RequestTemplate template = RequestTemplate.fromFile(templatePath);
        sendRequest(
                template.getMethod(),
                template.getPath(),
                template.getHeaders(),
                template.getBody(),
                outputPath
        );
    }

    public void sendRequest(String method, String path,
                            Map<String, String> headers,
                            String body,
                            String outputPath) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(30000);

            StringBuilder request = new StringBuilder();
            request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host).append("\r\n");

            defaultHeaders.forEach((key, value) ->
                    request.append(key).append(": ").append(value).append("\r\n")
            );

            headers.forEach((key, value) ->
                    request.append(key).append(": ").append(value).append("\r\n")
            );

            if (body != null && !body.isEmpty()) {
                request.append("Content-Length: ").append(body.getBytes().length).append("\r\n");
                request.append("\r\n").append(body);
            } else {
                request.append("\r\n");
            }

            logger.debug("Sending request:\n{}", request);
            socket.getOutputStream().write(request.toString().getBytes());

            processResponse(socket, outputPath);
        }
    }

    private void processResponse(Socket socket, String outputPath) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
        );

        String statusLine = reader.readLine();
        System.out.println(statusLine);

        if (!statusLine.contains("200")) {
            System.err.println("Error: Server returned status " + statusLine);
            return;
        }

        Map<String, String> responseHeaders = new HashMap<>();
        String line;
        int contentLength = 0;
        boolean chunked = false;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
            String[] parts = line.split(": ", 2);
            if (parts.length == 2) {
                responseHeaders.put(parts[0].toLowerCase(), parts[1]);
                if (parts[0].toLowerCase().equals("content-length")) {
                    contentLength = Integer.parseInt(parts[1]);
                }
                if (parts[0].toLowerCase().equals("transfer-encoding") &&
                        parts[1].toLowerCase().equals("chunked")) {
                    chunked = true;
                }
            }
        }

        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            if (chunked) {
                readChunkedBody(reader, fos, socket);
            } else if (contentLength > 0) {
                readFixedLengthBody(socket.getInputStream(), fos, contentLength);
            }
        }

        System.out.println("\nFile saved successfully: " + outputPath);
    }

    private void readChunkedBody(BufferedReader reader, FileOutputStream fos, Socket socket) throws IOException {
        String lengthLine;
        byte[] buffer = new byte[BUFFER_SIZE];
        InputStream is = socket.getInputStream();

        while ((lengthLine = reader.readLine()) != null) {
            int chunkLength = Integer.parseInt(lengthLine.trim(), 16);
            if (chunkLength == 0) break;

            int bytesRead;
            int remaining = chunkLength;

            while (remaining > 0 && (bytesRead = is.read(buffer, 0,
                    Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
                printProgress(chunkLength - remaining, chunkLength);
            }

            reader.readLine();
        }
    }

    private void readFixedLengthBody(InputStream is, FileOutputStream fos,
                                     int contentLength) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        int totalBytesRead = 0;

        while (totalBytesRead < contentLength &&
                (bytesRead = is.read(buffer, 0,
                        Math.min(buffer.length, contentLength - totalBytesRead))) != -1) {
            fos.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
            printProgress(totalBytesRead, contentLength);
        }
    }

    private void printProgress(long current, long total) {
        int width = 50;
        double percentage = (double) current / total;
        int progress = (int) (width * percentage);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < progress) {
                bar.append("=");
            } else if (i == progress) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("] ")
                .append(String.format("%.1f%%", percentage * 100))
                .append(String.format(" (%d/%d bytes)", current, total));

        System.out.print("\r" + bar);
        if (current >= total) {
            System.out.println();
        }
    }
}
