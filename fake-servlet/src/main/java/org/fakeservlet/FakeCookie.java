package org.fakeservlet;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * RFC 6265 compliant http cookie parser and generator future-proof
 * for future attributes
 *
 * <h2>Attributes</h2>
 * <p>
 * Boolean attributes are added without "=..." if true and omitted if false.
 * null attributes are omitted.
 * Instant attributes are formatted according to RFC 1123. The following
 * are standard attributes:
 *
 * <ul>
 *     <li>Max-Age</li>
 *     <li>Expires</li>
 *     <li>Secure</li>
 *     <li>HttpOnly</li>
 *     <li>Domain</li>
 *     <li>Path</li>
 * </ul>
 */
public class FakeCookie {
    private final String name;
    private final String value;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public FakeCookie(String name, String value) {
        this.name = name;
        this.value = value != null ? value : "";
        if (this.value.isEmpty()) {
            this.attributes.putIfAbsent("Expires", Instant.ofEpochMilli(0));
            this.attributes.putIfAbsent("Max-Age", 0);
        }
    }

    public static FakeCookie delete(String name) {
        return new FakeCookie(name, null);
    }

    public static FakeCookie parseSetCookieHeader(String rfc6264String) {
        int offset = 0;
        int startKey = scanToNot(rfc6264String, offset, List.of('\t', ' '));
        if (startKey == rfc6264String.length()) {
            throw new IllegalArgumentException("Illegal empty set-cookie header");
        }
        int endKey = scanTo(rfc6264String, startKey + 1, List.of('\t', ' ', '='));
        String key = rfc6264String.substring(startKey, endKey);
        int equalPos = scanTo(rfc6264String, endKey - 1, List.of('='));
        if (equalPos == -1) {
            throw new IllegalArgumentException("No value for rfc6264String string " + rfc6264String);
        }
        int startValue = scanToNot(rfc6264String, equalPos + 1, List.of('\t', ' '));
        String value;
        int endValue;
        if (rfc6264String.charAt(startValue) == '\"') {
            startValue = startValue + 1;
            endValue = scanTo(rfc6264String, startValue, List.of('"'));
            if (endValue == -1) {
                throw new IllegalArgumentException("Quoted rfc6264String value not terminated " + rfc6264String);
            }
            offset = scanTo(rfc6264String, endValue, List.of(';'));
        } else {
            endValue = scanTo(rfc6264String, startValue, List.of(';'));
            if (endValue == rfc6264String.length()) {
                offset = endValue;
            } else if (rfc6264String.charAt(endValue + 1) != ';') {
                offset = scanTo(rfc6264String, endValue, List.of(';'));
            }
        }
        value = URLDecoder.decode(rfc6264String.substring(startValue, endValue), StandardCharsets.UTF_8);
        FakeCookie cookie = new FakeCookie(key, value);

        while (offset < rfc6264String.length()) {
            int attributeStart = scanToNot(rfc6264String, offset + 1, List.of(' '));
            int attributeEnd = scanTo(rfc6264String, attributeStart, List.of('=', ';'));
            String attribute = rfc6264String.substring(attributeStart, attributeEnd);
            if (attributeEnd < rfc6264String.length() && rfc6264String.charAt(attributeEnd) == '=') {
                int attributeValueEnd = scanTo(rfc6264String, attributeEnd, List.of(';'));
                String attributeValue = rfc6264String.substring(attributeEnd + 1, attributeValueEnd);
                cookie.setAttribute(attribute, attributeValue);
                offset = attributeValueEnd;
            } else {
                cookie.setAttribute(attribute, true);
                offset = attributeEnd;
            }
        }
        return cookie;
    }

    public static Map<String, String> parseCookieHeader(String cookie) {
        Map<String, String> result = new LinkedHashMap<>();
        int offset = 0;
        while (offset < cookie.length()) {
            if (cookie.charAt(offset) == ';') {
                offset++;
            }

            int startKey = scanToNot(cookie, offset, List.of('\t', ' '));
            int endKey = scanTo(cookie, startKey + 1, List.of('\t', ' ', '='));
            String key = cookie.substring(startKey, endKey);
            int equalPos = scanTo(cookie, endKey - 1, List.of('='));
            if (equalPos == cookie.length()) {
                throw new IllegalArgumentException("No value for cookie string " + cookie);
            }
            int startValue = scanToNot(cookie, equalPos + 1, List.of('\t', ' '));
            String value;
            int endValue;
            if (cookie.charAt(startValue) == '\"') {
                startValue = startValue + 1;
                endValue = scanTo(cookie, startValue, List.of('"'));
                if (endValue == cookie.length()) {
                    throw new IllegalArgumentException("Quoted cookie value not terminated " + cookie);
                }
                offset = scanTo(cookie, endValue, List.of(';'));
            } else {
                offset = endValue = scanTo(cookie, startValue, List.of(';'));
            }
            value = cookie.substring(startValue, endValue);
            result.put(key, URLDecoder.decode(value, StandardCharsets.UTF_8));
        }

        return result;
    }

    private static int scanTo(String s, int startPos, List<Character> characters) {
        for (int i = startPos; i < s.length(); i++) {
            if (characters.contains(s.charAt(i))) {
                return i;
            }
        }
        return s.length();
    }

    private static int scanToNot(String s, int startPos, List<Character> characters) {
        for (int i = startPos; i < s.length(); i++) {
            if (!characters.contains(s.charAt(i))) {
                return i;
            }
        }
        return s.length();
    }

    public static Optional<String> asClientCookieHeader(Collection<FakeCookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cookies.stream()
                .map(FakeCookie::toClientCookieString)
                .collect(Collectors.joining(";")));
    }

    public static List<FakeCookie> parseSetCookieHeaders(List<String> setCookieHeaders) {
        if (setCookieHeaders == null) {
            return List.of();
        }
        return setCookieHeaders.stream().map(FakeCookie::parseSetCookieHeader)
                .collect(Collectors.toList());
    }

    public static Supplier<Map<String, List<String>>> parseClientCookieMap(List<String> cookieHeaders) {
        return new Supplier<>() {
            private final AtomicReference<Map<String, List<String>>> cookies = new AtomicReference<>();

            @Override
            public Map<String, List<String>> get() {
                Map<String, List<String>> result = cookies.get();
                if (result != null) {
                    return result;
                }
                return cookies.updateAndGet(cur -> parseClientCookies(cookieHeaders));
            }
        };
    }

    public static Map<String, List<String>> parseClientCookies(List<String> cookieHeaders) {
        if (cookieHeaders == null) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (String cookieHeader : cookieHeaders) {
            parseCookieHeader(cookieHeader).forEach((name, value) ->
                    result.computeIfAbsent(name, n -> new ArrayList<>())
                            .add(value));
        }
        return result;
    }


    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public String toStringRFC6265() {
        String attributeValues = cookieAttributeValues();
        return toClientCookieString() + (attributeValues.isEmpty() ? "" : "; " + attributeValues);
    }

    public String toClientCookieString() {
        String value = this.value.isEmpty() ? "" : "\"" + URLEncoder.encode(this.value, StandardCharsets.UTF_8) + "\"";
        return (name + "=" + value);
    }

    String cookieAttributeValues() {
        List<String> builder = new ArrayList<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                if ((Boolean) entry.getValue()) {
                    builder.add(entry.getKey());
                }
            } else if (entry.getValue() instanceof Instant) {
                Instant instant = (Instant) entry.getValue();
                builder.add(entry.getKey() + "=" + RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC)));
            } else if (entry.getValue() != null) {
                builder.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return String.join("; ", builder);
    }

    public boolean isUnexpired() {
        Object attribute = getAttribute("Max-Age");
        return attribute == null || (!attribute.equals(-1) && attribute.equals("-1"));
    }

    public FakeCookie domain(String domain) {
        return setAttribute("Domain", domain);
    }

    public FakeCookie maxAge(int maxAge) {
        return setAttribute("Max-age", maxAge != -1 ? String.valueOf(maxAge) : null);
    }

    public FakeCookie path(String path) {
        return setAttribute("Path", path);
    }

    public FakeCookie sameSite(String sameSite) {
        return setAttribute("SameSite", sameSite);
    }

    public FakeCookie secure(boolean secure) {
        return setAttribute("Secure", secure);
    }

    public boolean secure() {
        return getBooleanAttribute("Secure");
    }

    public FakeCookie httpOnly(boolean httpOnly) {
        return setAttribute("HttpOnly", httpOnly);
    }

    public boolean httpOnly() {
        return getBooleanAttribute("HttpOnly");
    }

    public FakeCookie setAttribute(String key, String value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
        return this;
    }

    public FakeCookie setAttribute(String key, Boolean value) {
        if (value == null || !value) {
            attributes.remove(key);
        } else {
            attributes.put(key, true);
        }
        return this;
    }

    private boolean getBooleanAttribute(String key) {
        return attributes.containsKey(key) && attributes.get(key) == Boolean.TRUE;
    }

    @Override
    public String toString() {
        return "FakeCookie{" +
               "name='" + name + '\'' +
               ", value='" + value + '\'' +
               ", attributes=" + attributes +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FakeCookie that = (FakeCookie) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, attributes);
    }
}

