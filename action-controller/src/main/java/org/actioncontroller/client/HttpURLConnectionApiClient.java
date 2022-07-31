package org.actioncontroller.client;

import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.HttpNotModifiedException;
import org.actioncontroller.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.actioncontroller.util.ExceptionUtil.softenException;

public class HttpURLConnectionApiClient implements ApiClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpURLConnectionApiClient.class);

    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final URL baseUrl;
    private final Map<String, HttpCookie> clientCookies = new HashMap<>();
    private KeyStore trustStore;
    private KeyStore keyStore;
    private byte[] requestBody;

    public HttpURLConnectionApiClient(String baseUrl) {
        this(IOUtil.asURL(baseUrl));
    }

    public HttpURLConnectionApiClient(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public URL getBaseUrl() {
        return baseUrl;
    }

    @Override
    public ClientExchange createExchange() {
        return new ClientExchange();
    }

    @Override
    public String getClientCookie(String key) {
        return Optional.ofNullable(clientCookies.get(key))
                .filter(HttpURLConnectionApiClient::isUnexpired)
                .map(HttpCookie::getValue)
                .orElse(null);
    }

    public void setTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }

    public void setTrustedCertificate(X509Certificate serverCertificate) throws GeneralSecurityException, IOException {
        setTrustStore(createTrustStore(serverCertificate, "server"));
    }

    public static KeyStore createTrustStore(X509Certificate certificate, String alias) throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry(alias, certificate);
        return trustStore;
    }

    public static KeyStore createKeyStore(String alias, Key key, X509Certificate certificate) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, "".toCharArray());
        keyStore.setKeyEntry(alias, key, "".toCharArray(), new X509Certificate[]{certificate});
        return keyStore;
    }

    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public static KeyManager[] getKeyManagers(KeyStore keyStore) {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, null);
            return keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw softenException(e);
        }
    }

    public void addClientKey(PrivateKey privateKey, X509Certificate certificate) throws GeneralSecurityException, IOException {
        if (keyStore == null) {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
        }
        keyStore.setKeyEntry(certificate.getSerialNumber().toString(), privateKey, null, new X509Certificate[]{certificate});
    }

    public void setRequestBody(byte[] requestBody) {
        this.requestBody = requestBody;
    }

    protected class ClientExchange implements ApiClientExchange {
        private String method;
        private String pathInfo;
        private final Map<String, String> requestHeaders = new HashMap<>();
        private final Map<String, List<String>> requestParameters = new HashMap<>();
        private HttpURLConnection connection;
        private final Map<String, String> requestCookies = getClientCookies();

        private List<HttpCookie> responseCookies = new ArrayList<>();
        private String errorBody;
        private KeyStore exchangeKeyStore = null;
        private String contentType;

        @Override
        public void setTarget(String method, String pathInfo) {
            this.method = method;
            int questionPos = pathInfo.indexOf('?');
            this.pathInfo = questionPos == -1 ? pathInfo : pathInfo.substring(0, questionPos);
        }

        @Override
        public void setClientCertificate(X509Certificate[] certificate) {
            if (certificate == null || certificate.length == 0 || certificate[0] == null) {
                exchangeKeyStore = null;
                return;
            }
            try {
                List<String> aliasDNs = new ArrayList<>();
                for (String alias : Collections.list(keyStore.aliases())) {
                    X509Certificate entry = (X509Certificate) keyStore.getCertificate(alias);
                    aliasDNs.add(entry.getSubjectDN().getName());
                    if (entry.getSerialNumber().equals(certificate[0].getSerialNumber())) {
                        this.exchangeKeyStore = createKeyStore(alias, HttpURLConnectionApiClient.this.keyStore.getKey(alias, null), certificate[0]);
                        return;
                    }
                }
                throw new IllegalArgumentException("Could not find key for " + certificate[0].getSubjectDN().getName() + " among " + aliasDNs);
            } catch (GeneralSecurityException | IOException e) {
                throw softenException(e);
            }
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
        public String getApiURL() {
            return baseUrl.toString();
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
            if (value instanceof Optional) {
                ((Optional<?>) value).ifPresent(v -> setRequestParameter(name, v));
            } else if (value instanceof Collection) {
                for (Object o : ((Collection<?>) value)) {
                    setRequestParameter(name, o);
                }
            } else {
                requestParameters.computeIfAbsent(name, n -> new ArrayList<>())
                        .add(value.toString());
            }
        }

        @Override
        public void addRequestCookie(String name, Object value) {
            possiblyOptionalToString(value, s -> requestCookies.put(name, s));
        }

        @Override
        public void setHeader(String name, Object value) {
            possiblyOptionalToString(value, s -> requestHeaders.put(name, s));
        }

        private void possiblyOptionalToString(Object value, Consumer<String> consumer) {
            if (value instanceof Optional) {
                ((Optional<?>)value).ifPresent(v -> consumer.accept(String.valueOf(v)));
            } else {
                consumer.accept(String.valueOf(value));
            }
        }

        @Override
        public void executeRequest() throws IOException {
            connection = openConnection(IOUtil.asURL(getRequestUrl()));
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            if (!requestCookies.isEmpty()) {
                connection.setRequestProperty("Cookie", requestCookies.entrySet().stream()
                        .map(c -> c.getKey() + "=\"" + c.getValue() + "\"")
                        .collect(Collectors.joining(",")));
            }
            requestHeaders.forEach(connection::setRequestProperty);
            if (trustStore != null || exchangeKeyStore != null) {
                try {
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(getKeyManagers(exchangeKeyStore), getTrustManagers(trustStore), null);
                    ((HttpsURLConnection)connection).setSSLSocketFactory(context.getSocketFactory());
                } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                    throw softenException(e);
                }
            }

            long startTime = System.currentTimeMillis();
            String query = getQuery();
            if (query != null && !isGetRequest()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.getOutputStream().write(query.getBytes());
                connection.getOutputStream().flush();
            } else if (requestBody != null && !isGetRequest()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-type", contentType);
                connection.getOutputStream().write(requestBody);
                connection.getOutputStream().flush();
            }

            int responseCode = connection.getResponseCode();
            logger.debug("\"{} {}\" {} latency={}",
                    connection.getRequestMethod(),
                    connection.getURL(),
                    responseCode,
                    (System.currentTimeMillis() - startTime)
            );

            Map<String, List<String>> headerFields = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> responseHeader : headerFields.entrySet()) {
                if ("set-cookie".equalsIgnoreCase(responseHeader.getKey())) {
                    responseCookies = new ArrayList<>();
                    for (String setCookieField : responseHeader.getValue()) {
                        responseCookies.addAll(HttpCookie.parse(setCookieField));
                    }
                    responseCookies.forEach(c -> clientCookies.put(c.getName(), c));
                }
            }
        }

        public String getRequestUrl() {
            String query = getQuery();
            return baseUrl + pathInfo + (query != null && isGetRequest() ? "?" + query : "");
        }

        private boolean isGetRequest() {
            return method.equals("GET");
        }

        private String getQuery() {
            if (!requestParameters.isEmpty()) {
                return requestParameters
                        .entrySet().stream()
                        .map(entry -> entry.getValue().stream().map(v -> urlEncode(entry.getKey()) + "=" + urlEncode(v)).collect(Collectors.joining("&")))
                        .collect(Collectors.joining("&"));
            }
            return null;
        }

        @Override
        public int getResponseCode() throws IOException {
            return connection.getResponseCode();
        }

        @Override
        public List<String> getResponseHeaders(String name) {
            if (connection == null) {
                return List.of();
            }
            return connection.getHeaderFields().entrySet().stream()
                    .filter(e -> name.equalsIgnoreCase(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseGet(ArrayList::new);
        }

        @Override
        public void checkForError() throws HttpClientException, IOException {
            if (getResponseCode() >= 400) {
                throw new HttpClientException(getResponseCode(), connection.getResponseMessage(), getErrorBody(), getRequestURL());
            } else if (getResponseCode() == 304) {
                throw new HttpNotModifiedException(null);
            }
        }

        @Override
        public List<String> getResponseCookies(String name) {
            return responseCookies.stream()
                    .filter(c -> c.getName().equals(name))
                    .filter(HttpURLConnectionApiClient::isUnexpired)
                    .map(httpCookie -> URLDecoder.decode(httpCookie.getValue(), CHARSET))
                    .collect(Collectors.toList());
        }

        @Override
        public Reader getResponseBodyReader() throws IOException {
            return new InputStreamReader(getResponseBodyStream());
        }

        @Override
        public InputStream getResponseBodyStream() throws IOException {
            return connection.getInputStream();
        }

        private String getErrorBody() throws IOException {
            if (errorBody != null || connection.getErrorStream() == null) {
                return errorBody;
            }
            return errorBody = asString(connection.getErrorStream());
        }

        @Override
        public void write(String contentType, ApiHttpExchange.WriterConsumer consumer) throws IOException {
            setContentType(contentType);
            StringWriter body = new StringWriter();
            consumer.accept(new PrintWriter(body));
            setRequestBody(body.toString().getBytes());
        }

        @Override
        public void output(String contentType, ApiHttpExchange.OutputStreamConsumer consumer) throws IOException {
            setContentType(contentType);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            consumer.accept(body);
            setRequestBody(body.toByteArray());
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public String toString() {
            return "ClientExchange{" + method + " " + getRequestUrl() + "}";
        }
    }

    private Map<String, String> getClientCookies() {
        return clientCookies.values().stream()
                .filter(HttpURLConnectionApiClient::isUnexpired)
                .filter(c -> !isHttps() || c.getSecure())
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getValue()));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static TrustManager[] getTrustManagers(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

    private boolean isHttps() {
        return baseUrl.getProtocol().equals("https");
    }

    private static boolean isUnexpired(HttpCookie c) {
        return c.getMaxAge() == -1 || c.getMaxAge() > 0;
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{baseUrl=" + baseUrl + " ,cookies=" + clientCookies.values().stream().map(HttpCookie::getName).collect(Collectors.joining(",")) + "}";
    }

    public static String asString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        inputStream.transferTo(buffer);
        return buffer.toString();
    }
}
