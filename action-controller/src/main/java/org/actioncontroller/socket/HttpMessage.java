package org.actioncontroller.socket;

import org.actioncontroller.ActionControllerCookie;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

public class HttpMessage {

    public static HttpMessage read(InputStream inputStream) throws IOException {
        return new HttpMessage(
                readLine(inputStream),
                readHttpHeaders(inputStream)
        );
    }

    private final String startLine;
    private final Map<String, List<String>> headers;
    private final Supplier<Map<String, List<String>>> requestCookies;

    public HttpMessage(String startLine, Map<String, List<String>> headers) {
        this.startLine = startLine;
        this.headers = headers;
        this.requestCookies = ActionControllerCookie.parseClientCookieMap(headers.get("Cookie"));
    }


    public String getStartLine() {
        return startLine;
    }

    public InputStream getInputStream(InputStream inputStream) throws IOException {
        // TODO: Content-Encoding: gzip|compress|deflate|br
        boolean chunked = firstHeader("Transfer-Encoding")
                .filter(value -> value.equalsIgnoreCase("chunked"))
                .isPresent();
        if (chunked) {
            return new ChunkedInputStream(inputStream);
        }

        int contentLength = firstHeader("Content-Length")
                .map(Integer::parseInt)
                .orElseThrow(() -> new UnsupportedOperationException("Content-Length or Transfer-Encoding must be specified"));

        return new ContentLengthInputStream(inputStream, contentLength);
    }

    public byte[] readBodyBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        getInputStream(inputStream).transferTo(buffer);
        return buffer.toByteArray();
    }

    public String readBodyString(InputStream inputStream) throws IOException {
        StringWriter buffer = new StringWriter();
        getReader(inputStream).transferTo(buffer);
        return buffer.toString();
    }

    public Optional<String> firstHeader(String name) {
        return getAllHeaders().containsKey(name) ? Optional.of(headers.get(name).get(0)) : Optional.empty();
    }

    public List<String> getHeaders(String name) {
        return getAllHeaders().containsKey(name) ? headers.get(name) : List.of();
    }

    public Map<String, List<String>> getAllHeaders() {
        return headers;
    }

    public List<String> getRequestCookies(String name) {
        return requestCookies.get().get(name);
    }

    public Reader getReader(InputStream inputStream) throws IOException {
        return new InputStreamReader(getInputStream(inputStream));
    }

    public static Map<String, List<String>> readHttpHeaders(InputStream inputStream) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String headerLine;
        while (!(headerLine = readLine(inputStream)).trim().isEmpty()) {
            int colonPos = headerLine.indexOf(':');
            String headerName = headerLine.substring(0, colonPos).trim().toLowerCase();
            String headerValue = headerLine.substring(colonPos + 1).trim();
            headers.computeIfAbsent(headerName, k -> new ArrayList<>()).add(headerValue);
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

    public List<ActionControllerCookie> getResponseCookies() {
        return ActionControllerCookie.parseSetCookieHeaders(headers.get("Set-Cookie"));
    }

}
