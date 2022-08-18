package org.actioncontroller.socket;

import org.actioncontroller.ActionControllerCookie;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.exceptions.HttpNotModifiedException;
import org.actioncontroller.util.HttpUrl;
import org.actioncontroller.util.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.Socket;
import java.net.URL;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
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
    private final URL baseUrl;
    private final Map<String, ActionControllerCookie> clientCookies = new HashMap<>();

    public SocketHttpClient(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public URL getBaseUrl() {
        return baseUrl;
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

    @Override
    public String getClientCookie(String key) {
        return Optional.ofNullable(clientCookies.get(key))
                .filter(ActionControllerCookie::isUnexpired)
                .map(ActionControllerCookie::getValue)
                .orElse(null);
    }

    private class SocketApiClientExchange implements ApiClientExchange {
        private String method;
        private String pathInfo;
        private URL url;

        private final Map<String, List<String>> requestParameters = new TreeMap<>();
        private final Map<String, String> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private final List<ActionControllerCookie> requestCookies = new ArrayList<>();

        private Integer responseCode;
        private String responseMessage;
        private ApiHttpExchange.OutputStreamConsumer consumer;
        private ByteArrayOutputStream requestBody;

        private Socket socket;
        private HttpMessage response;

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
            String query = HttpUrl.getQuery(requestParameters);
            return IOUtil.asURL(baseUrl + pathInfo + (query != null && isGet() ? "?" + query : ""));
        }

        private boolean isGet() {
            return method.equalsIgnoreCase("GET");
        }

        @Override
        public void setRequestParameter(String name, Object value) {
            if (value instanceof Optional) {
                ((Optional<?>) value).ifPresent(v -> setRequestParameter(name, v));
            } else if (value instanceof Collection) {
                for (Object o : ((Collection<?>) value)) {
                    setRequestParameter(name, o);
                }
            } else {
                requestParameters.computeIfAbsent(name, n -> new ArrayList<>()).add(value.toString());
            }
        }

        @Override
        public String getApiURL() {
            return baseUrl.toString();
        }

        @Override
        public void addRequestCookie(String name, Object value) {
            possiblyOptionalToString(
                    value,
                    s -> requestCookies.add(new ActionControllerCookie(name, s))
            );
        }

        @Override
        public void setHeader(String name, Object value) {
            possiblyOptionalToString(value, s -> requestHeaders.put(name, s));
        }

        @Override
        public void executeRequest() throws IOException {
            URL url = getRequestURL();

            setHeader("Host", url.getAuthority());
            Map<String, ActionControllerCookie> cookies = clientCookies.entrySet().stream()
                    .filter(entry -> entry.getValue().isUnexpired())
                    .filter(c -> !isHttps(url) || c.getValue().secure())
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            requestCookies.forEach(c -> cookies.put(c.getName(), c));
            setHeader("Cookie", ActionControllerCookie.asClientCookieHeader(cookies.values()));

            socket = new Socket(url.getHost(), url.getPort());

            String requestTarget = url.getPath();
            String query = HttpUrl.getQuery(requestParameters);
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

            response = HttpMessage.read(socket.getInputStream());
            String[] parts = response.getStartLine().split(" " , 3);
            this.responseCode = Integer.parseInt(parts[1]);
            this.responseMessage = parts[2];
            response.getResponseCookies().forEach(c -> clientCookies.put(c.getName(), c));
        }

        private boolean isHttps(URL url) {
            return url.getProtocol().equals("https");
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public List<String> getResponseHeaders(String name) {
            return response.getHeaders(name);
        }

        @Override
        public List<String> getResponseCookies(String name) {
            if (response == null) {
                return List.of();
            }
            return response.getResponseCookies().stream()
                    .filter(c -> c.getName().equals(name))
                    .filter(ActionControllerCookie::isUnexpired)
                    .map(ActionControllerCookie::getValue)
                    .collect(Collectors.toList());
        }

        @Override
        public Reader getResponseBodyReader() throws IOException {
            return response.getReader(socket.getInputStream());
        }

        private String readResponseBody() throws IOException {
            return response.readBodyString(socket.getInputStream());
        }

        @Override
        public InputStream getResponseBodyStream() throws IOException {
            return response.getInputStream(socket.getInputStream());
        }

        @Override
        public void checkForError() throws HttpClientException, IOException {
            if (getResponseCode() >= 400) {
                throw new HttpClientException(getResponseCode(), responseMessage, readResponseBody(), getRequestURL());
            } else if (getResponseCode() == 304) {
                throw new HttpNotModifiedException(null);
            }
        }

        @Override
        public void setClientCertificate(X509Certificate[] certificate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(String contentType, ApiHttpExchange.WriterConsumer consumer) throws IOException {
            setHeader("Content-type", contentType);
            requestBody = new ByteArrayOutputStream();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(requestBody))) {
                consumer.accept(writer);
            }
            setHeader("Content-length", requestBody.size());
        }

        @Override
        public void output(String contentType, ApiHttpExchange.OutputStreamConsumer consumer) throws IOException {
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
