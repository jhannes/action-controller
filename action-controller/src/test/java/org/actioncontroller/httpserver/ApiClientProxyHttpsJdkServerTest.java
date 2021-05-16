package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.actioncontroller.AbstractApiClientProxyHttpsTest;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.httpserver.ApiHandler;
import org.junit.Before;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

import static org.actioncontroller.client.HttpURLConnectionApiClient.createKeyStore;
import static org.actioncontroller.client.HttpURLConnectionApiClient.createTrustStore;
import static org.actioncontroller.client.HttpURLConnectionApiClient.getKeyManagers;
import static org.actioncontroller.client.HttpURLConnectionApiClient.getTrustManagers;

public class ApiClientProxyHttpsJdkServerTest extends AbstractApiClientProxyHttpsTest {

    @Before
    public void createServerAndClient() throws Exception {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(hostname, 0), 0);
        httpsServer.createContext("/test/certificates", new ApiHandler(new CertificateController()));
        HttpsConfigurator config = new HttpsConfigurator(createSslContext()) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                sslParams.setWantClientAuth(true);
                params.setSSLParameters(sslParams);
            }
        };
        httpsServer.setHttpsConfigurator(config);
        httpsServer.start();

        String baseUrl = "https://localhost:" + httpsServer.getAddress().getPort();
        apiClient = new HttpURLConnectionApiClient(baseUrl + "/test/certificates");
        apiClient.setTrustedCertificate(serverCertificate);
    }

    private SSLContext createSslContext() throws GeneralSecurityException, IOException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                getKeyManagers(createKeyStore(hostname, serverKeyPair.getPrivate(), serverCertificate)),
                getTrustManagers(createTrustStore(serverCertificate, "server")),
                null
        );
        return sslContext;
    }

}
