package org.actioncontroller;

import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.servlet.ActionControllerConfigurationException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Parameter;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Extract the HTTPS Client Certificate from the request
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@HttpParameterMapping(ClientCertificate.MapperFactory.class)
public @interface ClientCertificate {
    class MapperFactory implements HttpParameterMapperFactory<ClientCertificate> {
        @Override
        public HttpParameterMapper create(ClientCertificate annotation, Parameter parameter, ApiControllerContext context) {
            if (parameter.getType() == Optional.class) {
                return exchange -> {
                    X509Certificate[] clientCertificate = exchange.getClientCertificate();
                    return clientCertificate != null ? Optional.of(clientCertificate[0]) : Optional.empty();
                };
            } else if (parameter.getType().isAssignableFrom(X509Certificate.class)) {
                return exchange -> {
                    X509Certificate[] clientCertificate = exchange.getClientCertificate();
                    if (clientCertificate == null) {
                        throw new HttpRequestException(401, "Missing client certificate");
                    }
                    return clientCertificate[0];
                };
            } else {
                throw new ActionControllerConfigurationException("Illegal type " + parameter.getParameterizedType() + " for " + ClientCertificate.class);
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(ClientCertificate annotation, Parameter parameter) {
            if (parameter.getType() == Optional.class) {
                return (exchange, arg) -> {
                    ((Optional<?>)arg).ifPresent(c -> exchange.setClientCertificate(new X509Certificate[]{(X509Certificate) c}));
                };
            } else if (parameter.getType().isAssignableFrom(X509Certificate.class)) {
                return (exchange, arg) -> {
                    exchange.setClientCertificate(new X509Certificate[]{(X509Certificate) arg});
                };
            } else {
                throw new ActionControllerConfigurationException("Illegal type " + parameter.getParameterizedType() + " for " + ClientCertificate.class);
            }
        }
    }
}
