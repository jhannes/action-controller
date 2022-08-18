package org.actioncontroller.socket;

import java.io.IOException;
import java.io.InputStream;

// TODO: Support buffering
public class ContentLengthInputStream extends InputStream {
    private final InputStream inputStream;
    private final int contentLength;
    private int offset;

    public ContentLengthInputStream(InputStream inputStream, int contentLength) {
        this.inputStream = inputStream;
        this.contentLength = contentLength;
    }

    @Override
    public int read() throws IOException {
        if (++this.offset > contentLength) {
            return -1;
        }
        return inputStream.read();
    }

}
