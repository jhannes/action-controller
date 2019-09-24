package org.actioncontroller.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HttpURLConnectionApiClient implements ApiClient {
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private URL baseUrl;
    private List<HttpCookie> clientCookies = new ArrayList<>();
    private String responseBody;

    public HttpURLConnectionApiClient(String baseUrl) throws MalformedURLException {
        this.baseUrl = new URL(baseUrl);
    }

    @Override
    public ApiClientExchange createExchange() {
        return new ClientExchange();
    }

    private class ClientExchange implements ApiClientExchange {
        private String method;
        private String pathInfo;
        private Map<String, String> requestHeaders = new HashMap<>();
        private Map<String, String> requestParameters = new HashMap<>();
        private HttpURLConnection connection;
        private List<HttpCookie> requestCookies = new ArrayList<>(clientCookies);
        private List<HttpCookie> responseCookies;
        private String errorBody;

        @Override
        public void setTarget(String method, String pathInfo) {
            this.method = method;
            int questionPos = pathInfo.indexOf('?');
            this.pathInfo = questionPos == -1 ? pathInfo : pathInfo.substring(0, questionPos);
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
            this.pathInfo = pathInfo;
        }

        @Override
        public URL getRequestURL() {
            return connection.getURL();
        }

        @Override
        public void setRequestParameter(String name, Object value) {
            possiblyOptionalToString(value, s -> requestParameters.put(name, s));
        }

        @Override
        public void addRequestCookie(String name, Object value) {
            possiblyOptionalToString(value, s -> {
                HttpCookie cookie = new HttpCookie(name, s);
                cookie.setPath(baseUrl.getPath());
                requestCookies.add(cookie);
            });
        }

        @Override
        public void setHeader(String name, Object value) {
            possiblyOptionalToString(value, s -> requestHeaders.put(name, s));
        }

        private void possiblyOptionalToString(Object value, Consumer<String> consumer) {
            if (value instanceof Optional) {
                ((Optional)value).ifPresent(v -> consumer.accept(String.valueOf(v)));
            } else {
                consumer.accept(String.valueOf(value));
            }
        }

        @Override
        public void executeRequest() throws IOException {
            String query = getQuery();
            URL url = new URL(baseUrl + pathInfo +
                    (query != null && isGetRequest() ? "?" + query : ""));
            connection = openConnection(url);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Cookie",
                    requestCookies.stream().map(HttpCookie::toString).collect(Collectors.joining(",")));
            requestHeaders.forEach(connection::setRequestProperty);

            if (query != null && !isGetRequest()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.getOutputStream().write(query.getBytes());
                connection.getOutputStream().flush();
            }

            connection.getResponseCode();

            responseCookies = new ArrayList<>();
            String setCookieField = connection.getHeaderField("Set-Cookie");
            if (setCookieField != null) {
                responseCookies = HttpCookie.parse(setCookieField);
                clientCookies.addAll(responseCookies);
            }
        }

        private boolean isGetRequest() {
            return method.equals("GET");
        }

        private String getQuery() {
            if (!requestParameters.isEmpty()) {
                return requestParameters
                        .entrySet().stream()
                        .map(entry -> URLEncoder.encode(entry.getKey()) + "=" + URLEncoder.encode(entry.getValue()))
                        .collect(Collectors.joining("&"));
            }
            return null;
        }

        @Override
        public int getResponseCode() throws IOException {
            return connection.getResponseCode();
        }

        @Override
        public String getResponseHeader(String name) {
            return connection.getHeaderField(name);
        }

        @Override
        public void checkForError() throws HttpClientException, IOException {
            if (getResponseCode() >= 400) {
                throw new HttpClientException(getResponseCode(), connection.getResponseMessage(), getErrorBody(), getRequestURL());
            }
        }

        @Override
        public String getResponseCookie(String name) {
            return responseCookies.stream()
                    .filter(c -> c.getName().equals(name))
                    .filter(HttpURLConnectionApiClient::isUnexpired)
                    .map(httpCookie -> URLDecoder.decode(httpCookie.getValue(), CHARSET))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public String getResponseBody() throws IOException {
            if (responseBody == null) {
                responseBody = asString(connection.getInputStream());
            }
            return responseBody;
        }

        private String getErrorBody() throws IOException {
            if (errorBody == null && connection.getErrorStream() != null) {
                errorBody = asString(connection.getErrorStream());
            }
            return errorBody;
        }
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private static String asString(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            stringBuilder.append((char)c);
        }
        return stringBuilder.toString();
    }
}
