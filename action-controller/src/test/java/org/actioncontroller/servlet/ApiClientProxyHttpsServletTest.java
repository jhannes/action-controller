package org.actioncontroller.servlet;

import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.AbstractApiClientProxyHttpsTest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Before;

import javax.servlet.ServletContextEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.actioncontroller.client.HttpURLConnectionApiClient.createKeyStore;
import static org.actioncontroller.client.HttpURLConnectionApiClient.createTrustStore;

public class ApiClientProxyHttpsServletTest extends AbstractApiClientProxyHttpsTest {

    @Before
    public void createServerAndClient() throws Exception {
        Server server = new Server();
        ServletContextHandler handler = new ServletContextHandler();
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext()
                        .addServlet("testApi", new ApiServlet(new CertificateController()))
                        .addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);
        server.addConnector(getServerConnector(server, serverCertificate, hostname, serverKeyPair));

        server.start();
        apiClient = new HttpURLConnectionApiClient(server.getURI() + "/api");
        apiClient.setTrustedCertificate(serverCertificate);
    }

    private static ServerConnector getServerConnector(Server server, X509Certificate serverCertificate, String hostname, KeyPair serverKeyPair) throws GeneralSecurityException, IOException {
        SslContextFactory.Server sslConnectionFactory = new SslContextFactory.Server();
        sslConnectionFactory.setTrustStore(createTrustStore(serverCertificate, "server"));
        sslConnectionFactory.setKeyStore(createKeyStore(hostname, serverKeyPair.getPrivate(), serverCertificate));
        sslConnectionFactory.setWantClientAuth(true);
        ServerConnector connector = new ServerConnector(server, sslConnectionFactory);
        connector.setHost(hostname);
        return connector;
    }
}
