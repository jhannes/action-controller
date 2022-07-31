package org.fakeservlet.jakarta;

import org.fakeservlet.FakeCookie;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DANGER! Unfinished class! Implement methods as you go!
 */
public class FakeJakartaResponse implements HttpServletResponse {
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final FakeJakartaRequest request;
    private int statusCode = 200;
    private String statusMessage;
    private String contentType;
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String characterEncoding;

    public FakeJakartaResponse(FakeJakartaRequest request) {
        this.request = request;
    }

    @Override
    public void addCookie(Cookie c) {
        addHeader(
                "Set-Cookie",
                new FakeCookie(c.getName(), URLDecoder.decode(c.getValue(), StandardCharsets.UTF_8))
                        .domain(c.getDomain()).maxAge(c.getMaxAge()).path(c.getPath()).secure(c.getSecure()).httpOnly(c.isHttpOnly())
                        .toStringRFC6265()
        );
    }

    public List<String> getCookies(String name) {
        return streamCookies()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .map(FakeCookie::getValue)
                .collect(Collectors.toList());
    }

    public List<FakeCookie> getCookies() {
        return streamCookies().collect(Collectors.toList());
    }

    private Stream<FakeCookie> streamCookies() {
        return getHeaders("Set-Cookie").stream().map(FakeCookie::parse);
    }

    // TODO
    @Override
    public boolean containsHeader(String s) {
        throw unimplemented();
    }

    // TODO
    @Override
    public String encodeURL(String s) {
        return encodeUrl(s);
    }

    // TODO
    @Override
    public String encodeRedirectURL(String s) {
        return encodeRedirectUrl(s);
    }

    // TODO
    @Override
    public String encodeUrl(String s) {
        // TODO: Should include some session id stuff if needed
        return s;
    }

    // TODO
    @Override
    public String encodeRedirectUrl(String s) {
        return encodeURL(s);
    }

    @Override
    public void sendError(int sc, String msg) {
        setStatus(sc, msg);
    }

    @Override
    public void sendError(int sc) {
        sendError(sc, "Server Error");
    }

    @Override
    public void sendRedirect(String location) {
        statusCode = 302;

        // if location contains scheme, just return it
        // if location starts with / use request root url, append encodeRedirectUrl(location)
        // otherwise use request.getRequestURI() backtrack to "/" if needed and encodeRedirectUrl(location)
        if (location.startsWith("/")) {
            location = request.getAuthority() + request.getContextPath() + location;
        }

        setHeader("Location", encodeRedirectURL(location));
    }

    @Override
    public void setDateHeader(String name, long epochMillis) {
        setHeader(name, DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())));
    }

    @Override
    public void addDateHeader(String s, long l) {
        throw unimplemented();
    }

    @Override
    public void setHeader(String name, String value) {
        if (value == null) {
            headers.remove(name);
        } else {
            headers.put(name, List.of(value));
        }
    }

    @Override
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String s, int i) {
        throw unimplemented();
    }

    @Override
    public void setStatus(int sc) {
        setStatus(sc, null);
    }

    @Override
    public void setStatus(int sc, String reason) {
        this.statusCode = sc;
        this.statusMessage = reason;
    }

    @Override
    public int getStatus() {
        return statusCode;
    }

    @Override
    public String getHeader(String name) {
        return headers.containsKey(name) ? headers.get(name).get(0) : null;
    }

    // TODO
    @Override
    public List<String> getHeaders(String s) {
        return headers.getOrDefault(s, new ArrayList<>());
    }

    @Override
    public Collection<String> getHeaderNames() {
        throw unimplemented();
    }

    // TODO
    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final ServletOutputStream servletOutputStream = new ServletOutputStream() {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

        }

        @Override
        public void write(int b) {
            outputStream.write(b);
        }
    };

    @Override
    public ServletOutputStream getOutputStream() {
        return servletOutputStream;
    }

    private final PrintWriter writer = new PrintWriter(servletOutputStream);

    @Override
    public PrintWriter getWriter() {
        return writer;
    }

    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    private AssertionError unimplemented() {
        return new AssertionError("called unexpected method");
    }

    @Override
    public void setContentLength(int i) {
        setContentLengthLong(i);
    }

    @Override
    public void setContentLengthLong(long l) {
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public void setBufferSize(int i) {
        throw unimplemented();
    }

    @Override
    public int getBufferSize() {
        throw unimplemented();
    }

    // TODO
    @Override
    public void flushBuffer() {
        writer.flush();
    }

    @Override
    public void resetBuffer() {
        throw unimplemented();
    }

    @Override
    public boolean isCommitted() {
        throw unimplemented();
    }

    @Override
    public void reset() {
        throw unimplemented();
    }

    @Override
    public void setLocale(Locale locale) {
        throw unimplemented();
    }

    @Override
    public Locale getLocale() {
        throw unimplemented();
    }

    public void assertNoError() {
        if (statusCode >= 400) {
            throw new AssertionError("Unexpected error " + statusCode + " " + statusMessage);
        }
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public byte[] getBody() {
        flushBuffer();
        return outputStream.toByteArray();
    }

    public String getBodyString() {
        return new String(getBody());
    }

}
