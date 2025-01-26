package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;

import model.HttpRequest;
import model.HttpResponse;
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
            HttpRequest httpRequest = new HttpRequest(in);
            if(httpRequest.getMethod().equals("GET")) {responseGet(out, httpRequest);}
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseGet(OutputStream out, HttpRequest httpRequest) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        HttpResponse httpResponse = new HttpResponse();

        File file = getFile(httpRequest);
        setBody(file, httpResponse);

        log.info("Request URL: {}", file.getPath());
        log.info("Response Status: 200 OK");

        response200Header(dos, httpResponse);
        responseBody(dos, httpResponse);
    }

    private File getFile(HttpRequest httpRequest) {
        String filePath = "./webapp" + httpRequest.getUrl();
        return new File(filePath);
    }

    private void setBody(File file, HttpResponse httpResponse) throws IOException {
        if(isValidFile(file)){
            httpResponse.setContentType(Files.probeContentType(file.toPath()));
            httpResponse.setBody(Files.readAllBytes(file.toPath()));
        } else{
            httpResponse.setContentType("text/plain;charset=utf-8");
            httpResponse.setBody("Hello World".getBytes());
        }
    }

    private boolean isValidFile(File file) {
        return file.exists() && file.isFile();
    }

    private void response200Header(DataOutputStream dos, HttpResponse httpResponse) throws IOException {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: " + httpResponse.getContentType() + "\r\n");
            dos.writeBytes("Content-Length: " + httpResponse.getBody().length + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.info("error header");
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, HttpResponse httpResponse) {
        try {
            dos.write(httpResponse.getBody(), 0, httpResponse.getBody().length);
            dos.flush();
        } catch (IOException e) {
            log.info("error body");
            log.error(e.getMessage());
        }
    }
}
