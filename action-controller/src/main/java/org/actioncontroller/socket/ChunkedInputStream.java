package org.actioncontroller.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// TODO: Massively incomplete!
public class ChunkedInputStream extends ByteArrayInputStream {
    public ChunkedInputStream(InputStream inputStream) throws IOException {
        super(readAllContent(inputStream));
    }

    private static byte[] readAllContent(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int length;
        if ((length = readLength(inputStream)) > 0) {
            for (int i = 0; i < length; i++) {
                buffer.write(inputStream.read());
            }
        }
        return buffer.toByteArray();

    }

    private static int readLength(InputStream inputStream) throws IOException {
        String line = HttpMessage.readLine(inputStream);
        return line.length() > 0 ? Integer.parseInt(line, 16) : 0;
    }

}
