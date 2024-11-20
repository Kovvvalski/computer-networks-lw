package by.dlstudio.client;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

            // Парсинг URL
            String url = cmd.getOptionValue("url");
            if (url == null) {
                throw new ParseException("URL is required");
            }
            URL parsedUrl = new URL(url);

            // Создание клиента
            HttpClient client = new HttpClient(
                    parsedUrl.getHost(),
                    parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort()
            );

            // Обработка шаблона, если указан
            if (cmd.hasOption("template")) {
                client.executeTemplate(cmd.getOptionValue("template"));
                return;
            }

            // Подготовка запроса
            String method = cmd.getOptionValue("method", "GET");
            String path = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();

            // Сбор заголовков
            Map<String, String> headers = new HashMap<>();
            if (cmd.hasOption("header")) {
                for (String header : cmd.getOptionValues("header")) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            // Подготовка тела запроса
            String body = null;
            if (cmd.hasOption("data")) {
                body = cmd.getOptionValue("data");
            } else if (cmd.hasOption("file")) {
                body = Files.readString(Paths.get(cmd.getOptionValue("file")));
            }

            // Выполнение запроса
            client.sendRequest(method, path, headers, body);

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
                .required()
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
                        "  java -jar client.jar -u http://example.com/api/data\n" +
                        "  java -jar client.jar -X POST -u http://example.com/api/data -d '{\"key\":\"value\"}'\n" +
                        "  java -jar client.jar -u http://example.com -H \"Content-Type: application/json\" -f data.json\n" +
                        "  java -jar client.jar -t request-template.txt\n",
                true);
    }

    public void executeTemplate(String templatePath) throws IOException {
        RequestTemplate template = RequestTemplate.fromFile(templatePath);
        sendRequest(
                template.getMethod(),
                template.getPath(),
                template.getHeaders(),
                template.getBody()
        );
    }

    public void sendRequest(String method, String path,
                            Map<String, String> headers,
                            String body) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(30000); // 30 секунд таймаут

            // Формирование запроса
            StringBuilder request = new StringBuilder();
            request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host).append("\r\n");

            // Добавление дефолтных заголовков
            defaultHeaders.forEach((key, value) ->
                    request.append(key).append(": ").append(value).append("\r\n")
            );

            // Добавление пользовательских заголовков
            headers.forEach((key, value) ->
                    request.append(key).append(": ").append(value).append("\r\n")
            );

            // Добавление тела запроса
            if (body != null && !body.isEmpty()) {
                request.append("Content-Length: ").append(body.getBytes().length).append("\r\n");
                request.append("\r\n").append(body);
            } else {
                request.append("\r\n");
            }

            // Отправка запроса
            logger.debug("Sending request:\n{}", request);
            socket.getOutputStream().write(request.toString().getBytes());

            // Чтение ответа
            processResponse(socket);
        }
    }

    private void processResponse(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
        );

        // Чтение статуса и заголовков
        String statusLine = reader.readLine();
        System.out.println(statusLine);

        // Чтение заголовков
        String line;
        int contentLength = 0;
        boolean chunked = false;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
            if (line.toLowerCase().startsWith("transfer-encoding: chunked")) {
                chunked = true;
            }
        }

        // Чтение тела ответа
        if (chunked) {
            readChunkedBody(reader);
        } else if (contentLength > 0) {
            readFixedLengthBody(reader, contentLength);
        }
    }

    private void readChunkedBody(BufferedReader reader) throws IOException {
        String lengthLine;
        while ((lengthLine = reader.readLine()) != null) {
            int chunkLength = Integer.parseInt(lengthLine.trim(), 16);
            if (chunkLength == 0) break;

            char[] chunk = new char[chunkLength];
            reader.read(chunk, 0, chunkLength);
            System.out.print(new String(chunk));
            reader.readLine(); // Пропуск CRLF после чанка
        }
    }

    private void readFixedLengthBody(BufferedReader reader, int contentLength) throws IOException {
        char[] body = new char[contentLength];
        reader.read(body, 0, contentLength);
        System.out.print(new String(body));
    }
}
