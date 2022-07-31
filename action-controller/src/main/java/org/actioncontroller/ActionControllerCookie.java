package org.actioncontroller;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * RFC 6265 compliant http cookie parser and generator future-proof
 * for future attributes
 *
 * <h2>Attributes</h2>
 *
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
 *
 */
public class ActionControllerCookie {
    private final String name;
    private final String value;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public ActionControllerCookie(String name, String value) {
        this.name = name;
        this.value = value != null ? value : "";
        if (this.value.isEmpty()) {
            this.attributes.putIfAbsent("Expires", Instant.ofEpochMilli(0));
            this.attributes.putIfAbsent("Max-Age", 0);
        }
    }

    public static ActionControllerCookie delete(String name) {
        return new ActionControllerCookie(name, null);
    }

    public static ActionControllerCookie parse(String rfc6264String) {
        String[] parts = rfc6264String.split(";");
        int equalsPos = parts[0].indexOf('=');
        String name = parts[0].substring(0, equalsPos);
        String value = URLDecoder.decode(parts[0].substring(equalsPos + 1), StandardCharsets.UTF_8);
        ActionControllerCookie cookie = new ActionControllerCookie(name, value);
        for (int i = 1; i < parts.length; i++) {
            equalsPos = parts[i].indexOf('=');
            if (equalsPos < 0) {
                cookie.setAttribute(parts[i].trim(), true);
            } else {
                cookie.setAttribute(
                        parts[i].substring(0, equalsPos).trim(),
                        parts[i].substring(equalsPos +1)
                );
            }
        }
        return cookie;
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
        return (name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8))
                + (attributeValues.isEmpty() ? "" : "; " + attributeValues);
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

    public ActionControllerCookie domain(String domain) {
        return setAttribute("Domain", domain);
    }

    public ActionControllerCookie maxAge(int maxAge) {
        return setAttribute("Max-age", maxAge != -1 ? String.valueOf(maxAge) : null);
    }

    public ActionControllerCookie path(String path) {
        return setAttribute("Path", path);
    }

    public ActionControllerCookie sameSite(String sameSite) {
        return setAttribute("Same-site", sameSite);
    }

    public ActionControllerCookie secure(boolean secure) {
        return setAttribute("Secure", secure);
    }

    public ActionControllerCookie httpOnly(boolean httpOnly) {
        return setAttribute("HttpOnly", httpOnly);
    }

    public ActionControllerCookie setAttribute(String key, String value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
        return this;
    }

    public ActionControllerCookie setAttribute(String key, Boolean value) {
        if (value == null || !value) {
            attributes.remove(key);
        } else {
            attributes.put(key, true);
        }
        return this;
    }

}

