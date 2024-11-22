package by.dlstudio.server;

import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class HttpServer {
    private static final String OPTION_PORT = "p";
    private static final String OPTION_DIRECTORY = "d";
    private static final String OPTION_CONFIG = "c";
    private static final String OPTION_HELP = "h";
    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_DIRECTORY = ".";
    private static final String CONFIG_PORT = "port";
    private static final String CONFIG_DIRECTORY = "directory";
    private static final String CONFIG_HEADERS_PREFIX = "header.";

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
            options.addOption(OPTION_PORT, "port", true, "Server port");
            options.addOption(OPTION_DIRECTORY, "directory", true, "Root directory");
            options.addOption(OPTION_CONFIG, "config", true, "Path to configuration file");
            options.addOption(OPTION_HELP, "help", false, "Show help");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(OPTION_HELP)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("HttpServer", options);
                return;
            }

            if (!cmd.hasOption(OPTION_CONFIG)) {
                System.err.println("Error: --config option is required.");
                System.exit(1);
            }

            String configFilePath = cmd.getOptionValue(OPTION_CONFIG);
            Properties config = loadConfig(configFilePath);

            int port;
            if (cmd.hasOption(OPTION_PORT)) {
                port = Integer.parseInt(cmd.getOptionValue(OPTION_PORT));
            } else if (config.containsKey(CONFIG_PORT)) {
                port = Integer.parseInt(config.getProperty(CONFIG_PORT));
            } else {
                port = Integer.parseInt(DEFAULT_PORT);
                System.out.println("Using default port: " + DEFAULT_PORT);
            }

            String directory;
            if (cmd.hasOption(OPTION_DIRECTORY)) {
                directory = cmd.getOptionValue(OPTION_DIRECTORY);
            } else if (config.containsKey(CONFIG_DIRECTORY)) {
                directory = config.getProperty(CONFIG_DIRECTORY);
            } else {
                directory = DEFAULT_DIRECTORY;
                System.out.println("Using default directory: " + DEFAULT_DIRECTORY);
            }

            Map<String, String> defaultHeaders = loadHeadersFromConfig(config);

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

    public static Properties loadConfig(String filePath) throws IOException {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            config.load(fis);
        }
        return config;
    }

    public static Map<String, String> loadHeadersFromConfig(Properties config) {
        Map<String, String> headers = new HashMap<>();
        for (String key : config.stringPropertyNames()) {
            if (key.startsWith(CONFIG_HEADERS_PREFIX)) {
                String headerName = key.substring(CONFIG_HEADERS_PREFIX.length());
                headers.put(headerName, config.getProperty(key));
            }
        }
        return headers;
    }

    public void start() throws IOException {
        logger.info("Starting server on port {}", port);
        logger.info("Serving files from directory: {}", rootDirectory);
        logger.info("Default headers: {}", defaultHeaders);

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