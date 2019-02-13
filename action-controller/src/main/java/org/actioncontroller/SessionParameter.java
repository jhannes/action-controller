package org.actioncontroller;


import org.actioncontroller.meta.AbstractHttpRequestParameterMapping;
import org.actioncontroller.meta.HttpParameterMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(SessionParameter.SessionParameterMapping.class)
public @interface SessionParameter {

    class SessionParameterMapping extends AbstractHttpRequestParameterMapping {

        private SessionParameter sessionParam;

        public SessionParameterMapping(SessionParameter sessionParam, Parameter parameter) {
            super(parameter);
            this.sessionParam = sessionParam;
        }

        @Override
        public Object apply(HttpServletRequest req, Map<String, String> u) {
            if (parameter.getType() == Consumer.class) {
                return new Consumer<Object>() {
                    @Override
                    public void accept(Object o) {
                        if (sessionParam.invalidate()) {
                            req.getSession().invalidate();
                        }
                        req.getSession(true).setAttribute(sessionParam.value(), o);
                    }
                };
            }

            Object value = req.getSession().getAttribute(sessionParam.value());
            if (parameter.getType() == Optional.class) {
                return Optional.ofNullable(value);
            } else if (value != null) {
                return value;
            } else {
                throw new HttpRequestException(401, "Missing required session parameter " + sessionParam.value());
            }
        }
    }

    String value();

    boolean invalidate() default false;

}
