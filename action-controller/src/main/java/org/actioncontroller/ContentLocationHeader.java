package org.actioncontroller;

import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnValueMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;

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
            StringBuilder url = new StringBuilder();
            url.append(req.getScheme()).append("://").append(req.getRemoteHost());
            int defaultPort = req.getScheme().equals("http") ? 80 : (req.getScheme().equals("https") ? 443 : -1);
            if (req.getServerPort() != 0 && req.getServerPort() != defaultPort) {
                url.append(':').append(req.getServerPort());
            }
            return url.toString();
        }
    }
}

