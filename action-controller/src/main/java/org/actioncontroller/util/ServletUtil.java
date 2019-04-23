package org.actioncontroller.util;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public class ServletUtil {
    public static String getServerUrl(HttpServletRequest req) {
        String scheme = Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
        int port = Optional.ofNullable(req.getHeader("X-Forwarded-Port")).map(Integer::parseInt).orElse(req.getServerPort());
        String host = req.getServerName();
        int defaultSchemePort = scheme.equals("https") ? 443 : 80;

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);
        if (port != defaultSchemePort) {
            url.append(":").append(port);
        }
        return url.toString();
    }

    public static String getRemoteAddress(HttpServletRequest req) {
        return Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(req.getRemoteAddr());
    }
}
