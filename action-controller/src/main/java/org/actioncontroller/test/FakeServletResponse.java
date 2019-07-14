package org.actioncontroller.test;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
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

    @Override
    public void addCookie(Cookie cookie) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean containsHeader(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String encodeURL(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String encodeRedirectURL(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String encodeUrl(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String encodeRedirectUrl(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void sendError(int sc, String msg) {
        this.statusCode = sc;
        this.statusMessage = msg;
    }

    @Override
    public void sendError(int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void sendRedirect(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setDateHeader(String s, long l) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void addDateHeader(String s, long l) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }

    @Override
    public void addHeader(String s, String s1) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setIntHeader(String s, int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void addIntHeader(String s, int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setStatus(int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setStatus(int i, String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public int getStatus() {
        return statusCode;
    }

    @Override
    public String getHeader(String name) {
        return this.headers.get(name.toLowerCase());
    }

    @Override
    public Collection<String> getHeaders(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Collection<String> getHeaderNames() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getCharacterEncoding() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getContentType() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public ServletOutputStream getOutputStream() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public PrintWriter getWriter() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setCharacterEncoding(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setContentLength(int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setContentLengthLong(long l) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setContentType(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setBufferSize(int i) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public int getBufferSize() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void flushBuffer() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void resetBuffer() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isCommitted() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void reset() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setLocale(Locale locale) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Locale getLocale() {
        throw new AssertionError("called unexpected method");
    }

    public void assertNoError() {
        assertThat(statusCode).describedAs(statusMessage).isLessThan(400);
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
