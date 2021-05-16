package org.actioncontroller.values;

import org.actioncontroller.exceptions.ActionControllerConfigurationException;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.exceptions.HttpUnauthorizedException;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

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
                return this::getClientCertificate;
            } else if (parameter.getType().isAssignableFrom(X509Certificate.class)) {
                return exchange -> getClientCertificate(exchange)
                        .orElseThrow(() -> new HttpUnauthorizedException("Missing client certificate"));
            } else {
                throw new ActionControllerConfigurationException("Illegal type " + parameter.getParameterizedType() + " for " + ClientCertificate.class);
            }
        }

        public Optional<X509Certificate> getClientCertificate(ApiHttpExchange exchange) {
            X509Certificate[] clientCertificate = exchange.getClientCertificate();
            return clientCertificate != null ? Optional.of(clientCertificate[0]) : Optional.empty();
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(ClientCertificate annotation, Parameter parameter) {
            if (parameter.getType() == Optional.class) {
                return (exchange, arg) -> ((Optional<?>)arg).ifPresent(c -> setClientCertificate(exchange, c));
            } else if (parameter.getType().isAssignableFrom(X509Certificate.class)) {
                return this::setClientCertificate;
            } else {
                throw new ActionControllerConfigurationException("Illegal type " + parameter.getParameterizedType() + " for " + ClientCertificate.class);
            }
        }

        public void setClientCertificate(ApiClientExchange exchange, Object certificate) {
            exchange.setClientCertificate(new X509Certificate[]{ (X509Certificate) certificate });
        }
    }
}
