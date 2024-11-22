package by.dlstudio.server;

import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {
    private final int port;
    private final String rootDirectory;
    private final Map<String, String> defaultHeaders;
    private final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    public HttpServer(int port, String rootDirectory, Map<String, String> defaultHeaders) {
        this.port = port;
        this.rootDirectory = rootDirectory;
        this.defaultHeaders = defaultHeaders;
    }

    public static void main(String[] args) {
        try {
            // Парсинг аргументов командной строки
            Options options = new Options();
            options.addOption("p", "port", true, "Server port");
            options.addOption("d", "directory", true, "Root directory");
            options.addOption("h", "help", false, "Show help");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("HttpServer", options);
                return;
            }

            // Получение аргументов порта и директории
            int port = Integer.parseInt(cmd.getOptionValue("p", "8080"));
            String directory = cmd.getOptionValue("d", ".");

            // Настройка заголовков по умолчанию
            Map<String, String> defaultHeaders = new HashMap<>();
            defaultHeaders.put("Access-Control-Allow-Origin", "https://my-cool-site.com");
            defaultHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

            // Запуск сервера
            HttpServer server = new HttpServer(port, directory, defaultHeaders);
            server.start();
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number. Please provide a valid integer.");
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        logger.info("Starting server on port {}", port);
        logger.info("Serving files from directory: {}", rootDirectory);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from {}", clientSocket.getInetAddress());
                new Thread(new RequestHandler(clientSocket, rootDirectory, defaultHeaders, logger)).start();
            }
        } catch (IOException e) {
            logger.error("Error while running server: ", e);
            throw e;
        }
    }
}