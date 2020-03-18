package org.fakeservlet;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DANGER! Unfinished class! Implement methods as you go!
 */
public class FakeServletResponse implements HttpServletResponse {
    private int statusCode = 200;
    private String statusMessage;
    private Map<String, String> headers = new HashMap<>();
    private String contentType;
    private List<Cookie> cookies = new ArrayList<>();
    private String characterEncoding;

    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public String getCookie(String name) {
        return cookies.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().map(Cookie::getValue)
                .orElse(null);
    }

    public List<Cookie> getCookies() {
        return cookies;
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
        throw unimplemented();
    }

    // TODO
    @Override
    public String encodeRedirectUrl(String s) {
        return encodeURL(s);
    }

    @Override
    public void sendError(int sc, String msg) {
        this.statusCode = sc;
        this.statusMessage = msg;
    }

    @Override
    public void sendError(int sc) {
        this.statusCode = sc;
        this.statusMessage = "Server Error";
    }

    @Override
    public void sendRedirect(String location) {
        statusCode = 302;
        // TODO: should run through encodeRedirectURL
        setHeader("Location", location);
    }

    @Override
    public void setDateHeader(String s, long l) {
        throw unimplemented();
    }

    @Override
    public void addDateHeader(String s, long l) {
        throw unimplemented();
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }

    // TODO
    @Override
    public void addHeader(String s, String s1) {
        throw unimplemented();
    }

    @Override
    public void setIntHeader(String s, int i) {
        throw unimplemented();
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
        return this.headers.get(name.toLowerCase());
    }

    // TODO
    @Override
    public Collection<String> getHeaders(String s) {
        throw unimplemented();
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

    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private ServletOutputStream servletOutputStream = new ServletOutputStream() {
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

    private PrintWriter writer = new PrintWriter(servletOutputStream);

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
        throw unimplemented();
    }

    @Override
    public void setContentLengthLong(long l) {
        throw unimplemented();
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
        throw unimplemented();
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
        assertThat(statusCode).describedAs(statusMessage).isLessThan(400);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public byte[] getBody() {
        return outputStream.toByteArray();
    }

}
