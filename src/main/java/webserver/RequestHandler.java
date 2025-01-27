package webserver;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import db.DataBase;
import model.HttpRequest;
import model.HttpResponse;
import model.SecurityRules;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final Socket connection;

    private static final RequestHandler INSTANCE = new RequestHandler();

    private static final Map<String, BiConsumer<HttpRequest, OutputStream>> urlMappings = new HashMap<>();

    static {
        urlMappings.put("POST/user/create", wrapException(INSTANCE::createUser));
        urlMappings.put("POST/user/login", wrapException(INSTANCE::login));
        urlMappings.put("GET/user/list.html", wrapException(INSTANCE::getUserList));
    }

    private static <T, U> BiConsumer<T, U> wrapException(
            ThrowingBiConsumer<T, U, Exception> throwingConsumer) {
        return (t, u) -> {
            try {
                throwingConsumer.accept(t, u);
            } catch (Exception e) {
                throw new RuntimeException("Error while handling request", e);
            }
        };
    }

    private interface ThrowingBiConsumer<T, U, E extends Exception> {
        void accept(T t, U u) throws E;
    }

    private RequestHandler() {this.connection = null;}

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            HttpRequest httpRequest = new HttpRequest(in);
            log.info("method: {}", httpRequest.getMethod());
            log.info("url: {}", httpRequest.getUrl());
            handleRequest(httpRequest, out);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void handleRequest(HttpRequest httpRequest, OutputStream out) throws IOException {
        if(unauthorized(httpRequest)) {
            log.info("Unauthorized Request: {}", httpRequest.getUrl());
            response303Header(new DataOutputStream(out), "/user/login.html");
            return;
        }

        String key = httpRequest.getMethod() + httpRequest.getUrl();
        BiConsumer<HttpRequest, OutputStream> handler = urlMappings.get(key);
        if (handler != null) {
            handler.accept(httpRequest, out);
        } else {
            defaultResponse(httpRequest, out);
        }
    }

    private boolean unauthorized(HttpRequest httpRequest) {
        String cookie = httpRequest.getCookie();
        log.info("Cookie: {}", cookie);
        log.info("boolean: {}", cookie.equals("logined=true"));
        return SecurityRules.isProtectedPage(httpRequest.getUrl()) &&
                (cookie == null || !cookie.equals("logined=true"));
    }

    private void defaultResponse(HttpRequest httpRequest, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        HttpResponse httpResponse = new HttpResponse();

        File file = getFile(httpRequest);
        setBody(file, httpResponse);

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
            log.info("error header 200");
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

    private void response303Header(DataOutputStream dos, String redirectUrl) throws IOException {
        try {
            dos.writeBytes("HTTP/1.1 303 See Other \r\n");
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.info("error header 303");
            log.error(e.getMessage());
        }
    }

    private void createUser(HttpRequest httpRequest, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        Map<String, String> userInfo = httpRequest.getBody();
        String userId = userInfo.get("userId");
        String password = userInfo.get("password");
        String name = userInfo.get("name");
        String email = URLDecoder.decode(userInfo.get("email"), StandardCharsets.UTF_8);

        User user = new User(userId, password, name, email);

        DataBase.addUser(user);

        log.info("userID: {}", userId);
        response303Header(dos, "../index.html");
    }

    private void login(HttpRequest httpRequest, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        Map<String, String> loginInfo = httpRequest.getBody();
        String userId = loginInfo.get("userId");
        String password = loginInfo.get("password");

        User user = DataBase.findUserById(userId);
        if (isValidUser(user, password)) {
            response303HeaderWithCookie(dos, "../index.html", "logined=true");
        } else {
            response303HeaderWithCookie(dos, "login_failed.html", "logined=false");
        }
    }

    private boolean isValidUser(User user, String password) {
        return user != null && user.getUserId() != null && user.getPassword() != null && user.getPassword().equals(password);
    }

    private void response303HeaderWithCookie(DataOutputStream dos, String redirectUrl, String cookie) throws IOException {
        try {
            dos.writeBytes("HTTP/1.1 303 See Other \r\n");
            dos.writeBytes("Location: " + redirectUrl + "\r\n");
            dos.writeBytes("Set-cookie: " + cookie + "; Path=/\r\n");
                    dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.info("error header 303 with cookie");
            log.error(e.getMessage());
        }
    }

    private void getUserList(HttpRequest httpRequest, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        HttpResponse httpResponse = new HttpResponse();

        File file = getFile(httpRequest);
        String fileContent = editListFile(file);

        httpResponse.setContentType(Files.probeContentType(file.toPath()));
        httpResponse.setBody(fileContent.getBytes());

        response200Header(dos, httpResponse);
        responseBody(dos, httpResponse);
    }

    private String editListFile(File file) throws IOException {
        StringBuilder userList = new StringBuilder();
        int idx = 3;

        Collection<User> users = DataBase.findAll();

        for(User user : users){
            userList.append("<tr> <th scope=\"row\">")
                    .append(idx)
                    .append("</th> <td>")
                    .append(user.getUserId())
                    .append("</td> <td>")
                    .append(user.getName())
                    .append("</td> <td>")
                    .append(user.getEmail())
                    .append("</td><td><a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a></td> </tr>");
            idx++;
        }

        String fileContent = Files.readString(file.toPath());

        return fileContent.replace("${userList}", userList.toString());
    }
}
