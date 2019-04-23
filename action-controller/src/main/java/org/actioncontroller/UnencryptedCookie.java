package org.actioncontroller;

import org.actioncontroller.meta.AbstractHttpRequestParameterMapping;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;

import javax.servlet.http.Cookie;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookie.Factory.class)
public @interface UnencryptedCookie {

    String value();

    public class Factory implements HttpRequestParameterMappingFactory<UnencryptedCookie> {

        @Override
        public HttpRequestParameterMapping create(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                return (req, params, resp) -> {
                    return new Consumer<Object>() {
                        @Override
                        public void accept(Object o) {
                            Cookie cookie = new Cookie(name, o.toString());
                            cookie.setSecure(true);
                            cookie.setPath(req.getContextPath() + req.getServletPath());
                            resp.addCookie(cookie);
                        }
                    };
                };
            } else {
                return (req, params, resp) -> {
                    String cookie = Optional.ofNullable(req.getCookies()).map(Stream::of)
                            .flatMap(cookieStream -> cookieStream.filter(c -> c.getName().equalsIgnoreCase(name)).findAny())
                            .map(Cookie::getValue)
                            .orElse(null);
                    return AbstractHttpRequestParameterMapping.convertTo(
                            cookie,
                            name,
                            parameter
                    );
                };
            }
        }
    }
}
