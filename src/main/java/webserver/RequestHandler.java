package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            String header = read_header(in);
            String filePath = "./webapp" + get_url(header);
            File file = get_file(filePath);

            response(out, file);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private String read_header(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        return br.lines()
                .takeWhile(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private String get_url(String header) {
        return header.split(" ")[1];
    }

    private File get_file(String filePath) {return new File(filePath);}

    private void response(OutputStream out, File file) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        String content_type = "text/html;charset=utf-8";
        byte[] body = (file.exists() && file.isFile()) ? Files.readAllBytes(file.toPath()) : "Hello World".getBytes();

        log.info("Request URL: {}", file.getPath());
        log.info("Response Status: 200 OK");

        response200Header(dos, body.length, content_type);
        responseBody(dos, body);
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String contentType) throws IOException {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: " + contentType + "\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.info("error body");
            log.error(e.getMessage());
        }
    }
}
