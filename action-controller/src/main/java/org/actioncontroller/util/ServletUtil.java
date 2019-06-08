package org.actioncontroller.util;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public class ServletUtil {
    public static String getServerUrl(HttpServletRequest req) {
        String scheme = Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
        String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host"))
                .orElseGet(() -> Optional.ofNullable(req.getHeader("Host"))
                        .orElseGet(() -> req.getServerName() + ":" + req.getServerPort()));
        return scheme + "://" + host;
    }

    public static String getRemoteAddress(HttpServletRequest req) {
        return Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(req.getRemoteAddr());
    }
}
