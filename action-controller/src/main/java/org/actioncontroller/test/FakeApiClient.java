package org.actioncontroller.test;

import org.actioncontroller.exceptions.HttpNotModifiedException;
import org.actioncontroller.util.IOUtil;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FakeApiClient implements ApiClient {
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final URL contextRoot;
    private final String servletPath;
    private final Servlet servlet;
    private HttpSession session;
    private final Map<String, Cookie> clientCookies = new HashMap<>();
    private final List<String> clientCertificateDNs = new ArrayList<>();
    private Principal remoteUser;
    private final URL baseUrl;

    public FakeApiClient(URL contextRoot, String servletPath, Servlet servlet) {
        this.contextRoot = contextRoot;
        this.servletPath = servletPath;
        this.servlet = servlet;
        this.baseUrl = IOUtil.asURL(contextRoot + servletPath);
    }

    public ApiClientExchange createExchange() {
        return new FakeApiClientExchange(contextRoot, servletPath, remoteUser);
    }

    @Override
    public URL getBaseUrl() {
        return baseUrl;
    }

    @Override
    public void setTrustedCertificate(X509Certificate serverCertificate) {

    }

    @Override
    public void addClientKey(PrivateKey privateKey, X509Certificate certificate) {
        this.clientCertificateDNs.add(certificate.getSubjectDN().getName());
    }

    @Override
    public String getClientCookie(String key) {
        return Optional.ofNullable(clientCookies.get(key))
                .filter(FakeApiClient::isUnexpired)
                .map(Cookie::getValue)
                .orElse(null);
    }

    private static boolean isUnexpired(Cookie c) {
        return c.getMaxAge() == -1 || c.getMaxAge() > 0;
    }

    public void authenticate(Principal remoteUser) {
        this.remoteUser = remoteUser;
    }

    public class FakeApiClientExchange implements ApiClientExchange {
        private final String apiUrl;
        private final FakeServletRequest request;

        private final FakeServletResponse response;

        private FakeApiClientExchange(URL contextRoot, String servletPath, Principal remoteUser) {
            List<Cookie> requestCookies = clientCookies.values().stream()
                    .filter(FakeApiClient::isUnexpired)
                    .collect(Collectors.toList());

            request = new FakeServletRequest("GET", contextRoot, servletPath, "/");
            request.setSession(session);
            request.setCookies(requestCookies);
            this.apiUrl = contextRoot + servletPath;
            setRemoteUser(remoteUser);
            response = new FakeServletResponse(request);
        }

        @Override
        public void setTarget(String method, String pathInfo) {
            request.setMethod(method);
            int questionPos = pathInfo.indexOf('?');
            request.setPathInfo(questionPos == -1 ? pathInfo : pathInfo.substring(0, questionPos));
        }

        @Override
        public String getRequestMethod() {
            return request.getMethod();
        }

        @Override
        public String getApiURL() {
            return apiUrl;
        }

        @Override
        public String getPathInfo() {
            return request.getPathInfo();
        }

        @Override
        public void setPathInfo(String pathInfo) {
            request.setPathInfo(pathInfo);
        }

        @Override
        public URL getRequestURL() {
            return IOUtil.asURL(request.getRequestURL().toString());
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
                request.addParameter(name, value.toString());
            }
        }

        @Override
        public void addRequestCookie(String name, Object value) {
            possiblyOptionalToString(value, s -> request.setCookie(name, s));
        }

        @Override
        public void setHeader(String name, Object value) {
            possiblyOptionalToString(value, s -> request.addHeader(name, s));
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
            try {
                servlet.service(request, response);
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
            session = request.getSession(false);
            response.getCookies().forEach(c -> clientCookies.put(c.getName(), c));
        }

        public boolean isError() {
            return getResponseCode() >= 400;
        }

        @Override
        public int getResponseCode() {
            return response.getStatus();
        }

        @Override
        public void checkForError() throws HttpClientException {
            if (isError()) {
                throw new HttpClientException(getResponseCode(), response.getStatusMessage(), getErrorResponse(), getRequestURL());
            } else if (getResponseCode() == 304) {
                throw new HttpNotModifiedException(null);
            }
        }

        private String getErrorResponse() {
            for (String contentType : Optional.ofNullable(request.getHeader("Accept")).orElse("").split(";")) {
                if (contentType.trim().equalsIgnoreCase("application/json")) {
                    return "{\"message\":\"" + response.getStatusMessage() + "\"}";
                } else if (contentType.equalsIgnoreCase("text/html")) {
                    return "<body><h2>Error " + response.getStatus() + " " + response.getStatusMessage() +  "</h2><table><tr><th>MESSAGE:</th><td>" + response.getStatusMessage() + "</td></tr></table></body>";
                }
            }
            return "MESSAGE: " + response.getStatusMessage();
        }

        @Override
        public List<String> getResponseHeaders(String name) {
            return response.getHeaders(name);
        }

        @Override
        public List<String> getResponseCookies(String name) {
            return response.getCookies(name);
        }

        @Override
        public Reader getResponseBodyReader() {
            return new StringReader(new String(response.getBody()));
        }

        @Override
        public InputStream getResponseBodyStream() {
            return new ByteArrayInputStream(response.getBody());
        }

        @Override
        public void setClientCertificate(X509Certificate[] certificate) {
            if (certificate != null && certificate.length > 0 && certificate[0] != null) {
                if (!clientCertificateDNs.contains(certificate[0].getSubjectDN().getName())) {
                    throw new IllegalArgumentException("Could not find key for " + certificate[0].getSubjectDN().getName() + " among " + clientCertificateDNs);
                }
                request.setAttribute("javax.servlet.request.X509Certificate", certificate);
            } else {
                request.removeAttribute("javax.servlet.request.X509Certificate");
            }
        }

        @Override
        public void write(String contentType, WriterConsumer consumer) throws IOException {
            setHeader("Content-type", contentType);
            StringWriter body = new StringWriter();
            consumer.accept(new PrintWriter(body));
            request.setRequestBody(body.toString().getBytes());
        }

        @Override
        public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
            setHeader("Content-type", contentType);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            consumer.accept(body);
            request.setRequestBody(body.toByteArray());
        }

        public void setRemoteUser(Object remoteUser) {
            if (remoteUser == null) {
                request.setUserPrincipal(null);
            } else if (remoteUser instanceof Principal) {
                request.setUserPrincipal((Principal)remoteUser);
            } else {
                request.setUserPrincipal(remoteUser::toString);
            }
        }
    }
}
