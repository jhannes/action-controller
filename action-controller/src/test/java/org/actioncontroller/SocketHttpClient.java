package org.actioncontroller;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SocketHttpClient {
    public static Map<String, String> readHttpHeaders(InputStream inputStream) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while (!(headerLine = readLine(inputStream)).trim().isEmpty()) {
            int colonPos = headerLine.indexOf(':');
            String headerName = headerLine.substring(0, colonPos).trim().toLowerCase();
            String headerValue = headerLine.substring(colonPos + 1).trim();
            headers.put(headerName, headerValue);
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
            line.append((char)c);
        }
        return line.toString();
    }
}
