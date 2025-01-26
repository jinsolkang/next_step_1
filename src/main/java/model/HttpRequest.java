package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static util.HttpRequestUtils.parseQueryString;
import static util.IOUtils.readData;

public class HttpRequest {
    private String method;
    private String url;
    private String httpVersion;
    private String host;
    private String connection;
    private int contentLength;
    private String contentType;
    private String accept;

    private Map<String, String> body;
    private String cookie;

    public HttpRequest(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        String requestMessage = readHttpRequest(br);
        Map<String, String> fields = parseHeader(requestMessage);
        matchField(fields);
        setBody(br);
    }

    private String readHttpRequest(BufferedReader br) throws IOException {
        return br.lines()
                .takeWhile(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private Map<String, String> parseHeader(String header) {
        List<String> lines = Arrays.asList(header.split("\n"));
        matchFirstLine(lines.get(0).split(" "));

        return lines.stream()
                .skip(1)
                .filter(line -> line.contains(":"))
                .map(line -> line.split(":", 2))
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),
                        parts -> parts[1].trim()
                ));
    }

    private void matchFirstLine(String[] firstLine) {
        this.method = firstLine[0];
        this.url = firstLine[1];
        this.httpVersion = firstLine[2];
    }

    private void matchField(Map<String, String> fields) {
        Class<?> clazz = this.getClass();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String headerKey = entry.getKey();
            String headerValue = entry.getValue();

            try {
                Field field = clazz.getDeclaredField(headerKey);
                field.setAccessible(true);
                field.set(this, headerValue);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }
    }

    private void setBody(BufferedReader br) throws IOException {
        if(this.contentLength > 0) {
            String bodyContent = readData(br, this.contentLength);
            this.body = parseQueryString(bodyContent);
        }
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public String getHost() {
        return host;
    }

    public String getConnection() {
        return connection;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public String getAccept() {
        return accept;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public String getCookie() {
        return cookie;
    }
}
