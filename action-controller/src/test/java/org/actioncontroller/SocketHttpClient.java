package org.actioncontroller;

import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpCookie;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Don't use this class in production code. It was just made on a dare
 */
public class SocketHttpClient implements ApiClient {
    private final String baseUrl;
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final Map<String, HttpCookie> clientCookies = new HashMap<>();

    public SocketHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static Map<String, String> readHttpHeaders(InputStream inputStream) throws IOException {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String headerLine;
        while (!(headerLine = readLine(inputStream)).trim().isEmpty()) {
            int colonPos = headerLine.indexOf(':');
            String headerName = headerLine.substring(0, colonPos).trim().toLowerCase();
            String headerValue = headerLine.substring(colonPos + 1).trim();
            headers.put(headerName, headerValue);
        }
        return headers;
    }

    public static String readLine(InputStream inputStream) throws IOException {
        int c;
        StringBuilder line = new StringBuilder();
        while ((c = inputStream.read()) != -1) {
            if (c == '\r') {
                inputStream.read();
                break;
            } else if (c == '\n') {
                break;
            }
            line.append((char) c);
        }
        return line.toString();
    }

    @Override
    public ApiClientExchange createExchange() {
        return new SocketApiClientExchange();
    }

    @Override
    public void setTrustedCertificate(X509Certificate serverCertificate) {

    }

    @Override
    public void addClientKey(PrivateKey privateKey, X509Certificate certificate) {

    }

    private static boolean isUnexpired(HttpCookie c) {
        return c.getMaxAge() == -1 || c.getMaxAge() > 0;
    }

    private class SocketApiClientExchange implements ApiClientExchange {
        private String method;
        private String pathInfo;
        private URL url;

        private final Map<String, String> requestParameters = new TreeMap<>();
        private final Map<String, String> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private List<HttpCookie> responseCookies = new ArrayList<>();
        private final List<HttpCookie> requestCookies = clientCookies.values().stream()
                .filter(SocketHttpClient::isUnexpired)
                .collect(Collectors.toList());

        private Integer responseCode;
        private String responseMessage;
        private Map<String, String> responseHeaders;
        private OutputStreamConsumer consumer;
        private ByteArrayOutputStream requestBody;

        private Socket socket;

        @Override
        public void setTarget(String method, String pathInfo) {
            this.method = method;
            setPathInfo(pathInfo);
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        public void setPathInfo(String pathInfo) {
            int questionPos = pathInfo.indexOf('?');
            this.pathInfo = questionPos == -1 ? pathInfo : pathInfo.substring(0, questionPos);
        }

        @Override
        public URL getRequestURL() {
            String query = getQuery();
            return IOUtil.asURL(baseUrl + pathInfo + (query != null && isGet() ? "?" + query : ""));
        }

        private boolean isGet() {
            return method.equalsIgnoreCase("GET");
        }

        private String getQuery() {
            if (!requestParameters.isEmpty()) {
                return requestParameters
                        .entrySet().stream()
                        .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                        .collect(Collectors.joining("&"));
            }
            return null;
        }

        private String urlEncode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        @Override
        public void setRequestParameter(String name, Object value) {
            possiblyOptionalToString(value, s -> requestParameters.put(name, s));
        }

        @Override
        public String getApiURL() {
            return baseUrl;
        }

        @Override
        public void addRequestCookie(String name, Object value) {
            possiblyOptionalToString(value, s -> {
                HttpCookie cookie = new HttpCookie(name, s);
                requestCookies.add(cookie);
            });
        }

        @Override
        public void setHeader(String name, Object value) {
            possiblyOptionalToString(value, s -> requestHeaders.put(name, s));
        }

        @Override
        public void executeRequest() throws IOException {
            URL url = getRequestURL();

            setHeader("Host", url.getAuthority());
            if (!requestCookies.isEmpty()) {
                setHeader("Cookie", requestCookies.stream()
                        .filter(c -> isHttps() || !c.getSecure())
                        .map(c -> c.getName() + "=\"" + c.getValue() + "\"")
                        .collect(Collectors.joining(",")));
            }

            socket = new Socket(url.getHost(), url.getPort());

            String requestTarget = url.getPath();
            String query = getQuery();
            if (query != null) {
                if (isGet()) {
                    requestTarget += "?" + query;
                } else if (requestBody == null) {
                    write("application/x-www-form-urlencoded", writer -> writer.write(query));
                }
            }
            socket.getOutputStream().write((method.toUpperCase() + " " + requestTarget + " HTTP/1.1\r\n").getBytes());
            for (Map.Entry<String, String> requestHeader : requestHeaders.entrySet()) {
                socket.getOutputStream().write((requestHeader.getKey() + ": " + requestHeader.getValue() + "\r\n").getBytes());
            }
            socket.getOutputStream().write("\r\n".getBytes());

            if (requestBody != null) {
                new ByteArrayInputStream(requestBody.toByteArray()).transferTo(socket.getOutputStream());
            }
            socket.getOutputStream().flush();

            String responseLine = readLine(socket.getInputStream());

            String[] parts = responseLine.split(" " , 3);
            this.responseCode = Integer.parseInt(parts[1]);
            this.responseMessage = parts[2];

            responseHeaders = readHttpHeaders(socket.getInputStream());
            responseCookies = new ArrayList<>();
            String setCookieField = getResponseHeader("Set-Cookie");
            if (setCookieField != null) {
                responseCookies = HttpCookie.parse(setCookieField);
                responseCookies.forEach(c -> clientCookies.put(c.getName(), c));
            }
        }

        private boolean isHttps() {
            return false;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getResponseHeader(String name) {
            return responseHeaders.get(name);
        }

        @Override
        public Optional<String> getResponseCookie(String name) {
            return responseCookies.stream()
                    .filter(c -> c.getName().equals(name))
                    .filter(SocketHttpClient::isUnexpired)
                    .map(httpCookie -> URLDecoder.decode(httpCookie.getValue(), CHARSET))
                    .findFirst();
        }

        @Override
        public String getResponseBody() throws IOException {
            if ("chunked".equalsIgnoreCase(responseHeaders.get("transfer-encoding"))) {
                StringBuilder buffer = new StringBuilder();

                int length;
                if ((length = readLength(socket.getInputStream())) > 0) {
                    for (int i = 0; i < length; i++) {
                        buffer.append((char)socket.getInputStream().read());
                    }
                }
                return buffer.toString();
            }

            int contentLength = Integer.parseInt(responseHeaders.get("Content-length"));

            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < contentLength; i++) {
                buffer.append((char)socket.getInputStream().read());
            }

            return buffer.toString();
        }

        private int readLength(InputStream inputStream) throws IOException {
            String line = readLine(inputStream);
            return line.length() > 0 ? Integer.parseInt(line, 16) : 0;
        }

        @Override
        public byte[] getResponseBodyBytes() throws IOException {
            if (responseHeaders.get("transfer-encoding").equalsIgnoreCase("chunked")) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int length;
                if ((length = readLength(socket.getInputStream())) > 0) {
                    for (int i = 0; i < length; i++) {
                        buffer.write(socket.getInputStream().read());
                    }
                }
                return buffer.toByteArray();
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkForError() throws HttpClientException, IOException {
            if (getResponseCode() >= 400) {
                throw new HttpClientException(getResponseCode(), responseMessage, getResponseBody(), getRequestURL());
            }
        }

        @Override
        public void setClientCertificate(X509Certificate[] certificate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(String contentType, WriterConsumer consumer) throws IOException {
            setHeader("Content-type", contentType);
            requestBody = new ByteArrayOutputStream();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(requestBody))) {
                consumer.accept(writer);
            }
            setHeader("Content-length", requestBody.size());
        }

        @Override
        public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
            setHeader("Content-type", contentType);
            requestBody = new ByteArrayOutputStream();
            consumer.accept(requestBody);
            setHeader("Content-length", requestBody.size());
        }

        private void possiblyOptionalToString(Object value, Consumer<String> consumer) {
            if (value instanceof Optional) {
                ((Optional<?>)value).ifPresent(v -> consumer.accept(String.valueOf(v)));
            } else {
                consumer.accept(String.valueOf(value));
            }
        }
    }
}
