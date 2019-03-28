package org.actioncontroller;

import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnValueMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;
import java.util.Optional;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(ContentLocationHeader.MappingFactory.class)
public @interface ContentLocationHeader {

    class MappingFactory implements HttpReturnMapperFactory<ContentLocationHeader> {
        @Override
        public HttpReturnValueMapping create(ContentLocationHeader annotation, Class<?> returnType) {
            if (returnType == URL.class) {
                return (result, resp, req) -> resp.setHeader("Content-location", result.toString());
            }
            return (result, resp, req) ->
                    resp.setHeader("Content-Location",
                            getServerUrl(req) + req.getContextPath() + req.getServletPath() + result);
        }

        private String getServerUrl(HttpServletRequest req) {
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
    }
}

