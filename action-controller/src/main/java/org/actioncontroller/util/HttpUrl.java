package org.actioncontroller.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpUrl {
    public static Map<String, List<String>> parseParameters(String query) {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String parameterString : query.split("&")) {
            int equalsPos = parameterString.indexOf('=');
            if (equalsPos > 0) {
                String paramName = parameterString.substring(0, equalsPos);
                String paramValue = URLDecoder.decode(parameterString.substring(equalsPos + 1), StandardCharsets.ISO_8859_1);
                result.computeIfAbsent(paramName, n -> new ArrayList<>()).add(paramValue);
            }
        }
        return result;
    }

    public static String getQuery(Map<String, List<String>> parameters) {
        if (!parameters.isEmpty()) {
            return parameters
                    .entrySet().stream()
                    .map(entry -> entry.getValue().stream().map(v -> urlEncode(entry.getKey()) + "=" + urlEncode(v)).collect(Collectors.joining("&")))
                    .collect(Collectors.joining("&"));
        }
        return null;
    }

    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
