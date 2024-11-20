package by.dlstudio.client;

import lombok.Getter;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class RequestTemplate {
    private String method;
    private String path;
    private final Map<String, String> headers;
    private String body;

    public RequestTemplate() {
        this.headers = new HashMap<>();
    }

    public static RequestTemplate fromFile(String path) throws IOException {
        RequestTemplate template = new RequestTemplate();
        List<String> lines = Files.readAllLines(Paths.get(path));

        enum ParseState { HEADERS, BODY }
        ParseState state = ParseState.HEADERS;
        StringBuilder bodyBuilder = new StringBuilder();

        for (String line : lines) {
            if (state == ParseState.HEADERS) {
                if (line.trim().isEmpty()) {
                    state = ParseState.BODY;
                    continue;
                }

                if (line.startsWith("METHOD:")) {
                    template.method = line.substring(7).trim();
                } else if (line.startsWith("URL:")) {
                    URL url = new URL(line.substring(4).trim());
                    template.path = url.getPath();
                } else if (line.startsWith("HEADERS:")) {
                    continue;
                } else if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    template.headers.put(parts[0].trim(), parts[1].trim());
                }
            } else {
                bodyBuilder.append(line).append("\n");
            }
        }

        if (!bodyBuilder.isEmpty()) {
            template.body = bodyBuilder.toString().trim();
        }

        return template;
    }
}
