package org.actioncontroller.client;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
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
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.actioncontroller.ExceptionUtil.softenException;

public class HttpURLConnectionApiClient implements ApiClient {
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private URL baseUrl;
    private Map<String, HttpCookie> clientCookies = new HashMap<>();
    private String responseBody;
    private KeyStore trustStore;
    private KeyStore keyStore;

    public HttpURLConnectionApiClient(String baseUrl) throws MalformedURLException {
        this.baseUrl = new URL(baseUrl);
    }

    @Override
    public ApiClientExchange createExchange() {
        return new ClientExchange();
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

    private class ClientExchange implements ApiClientExchange {
        private String method;
        private String pathInfo;
        private Map<String, String> requestHeaders = new HashMap<>();
        private Map<String, String> requestParameters = new HashMap<>();
        private HttpURLConnection connection;
        private List<HttpCookie> requestCookies = clientCookies.values().stream()
                .filter(HttpURLConnectionApiClient::isUnexpired)
                .collect(Collectors.toList());
        private List<HttpCookie> responseCookies;
        private String errorBody;
        private KeyStore exchangeKeyStore = null;

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
                ((Optional<?>)value).ifPresent(v -> consumer.accept(String.valueOf(v)));
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
            if (!requestCookies.isEmpty()) {
                connection.setRequestProperty("Cookie", requestCookies.stream()
                        .filter(c -> isHttps() || !c.getSecure())
                        .map(c -> c.getName() + "=\"" + c.getValue() + "\"")
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
                responseCookies.forEach(c -> clientCookies.put(c.getName(), c));
            }
        }

        private boolean isHttps() {
            return baseUrl.getProtocol().equals("https");
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

    public static TrustManager[] getTrustManagers(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
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
        StringBuilder stringBuilder = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            stringBuilder.append((char)c);
        }
        return stringBuilder.toString();
    }
}
