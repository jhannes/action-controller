package org.actioncontroller.socket;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class HttpInputStream extends InputStream {
    private final Integer contentLength;
    private final boolean chunked;
    private final InputStream inputStream;
    private int offset = 0;

    public HttpInputStream(InputStream inputStream, Map<String, List<String>> requestHeaders) {
        this.inputStream = inputStream;
        this.contentLength = requestHeaders.containsKey("Content-Length") ?
                Integer.parseInt(requestHeaders.get("Content-Length").get(0)) : null;
        this.chunked = requestHeaders.containsKey("Transfer-Encoding") && "chunked".equalsIgnoreCase(requestHeaders.get("Transfer-Encoding").get(0));

        if (chunked) {
            throw new UnsupportedOperationException();
        }
        if (contentLength == null) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public int read() throws IOException {
        if (++this.offset > contentLength) {
            return -1;
        }
        return inputStream.read();
    }
}
