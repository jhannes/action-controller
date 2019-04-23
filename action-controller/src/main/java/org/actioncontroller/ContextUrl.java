package org.actioncontroller;

import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.net.URL;

import static org.actioncontroller.util.ServletUtil.getServerUrl;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(ContextUrl.MappingFactory.class)
public @interface ContextUrl {

    public class MappingFactory implements HttpRequestParameterMappingFactory<ContextUrl> {
        @Override
        public HttpRequestParameterMapping create(ContextUrl annotation, Parameter parameter) {
            if (parameter.getType() == URL.class) {
                return (req, path, resp) -> new URL(getServerUrl(req) + req.getContextPath());
            } else if (parameter.getType() == String.class) {
                return (req, path, resp) -> getServerUrl(req) + req.getContextPath();
            }
            throw new IllegalArgumentException("Can't map " + parameter);
        }
    }
}
