package org.actioncontroller;

import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.util.ExceptionUtil;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.event.Level;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.Instant;

import static org.actioncontroller.client.HttpURLConnectionApiClient.createKeyStore;
import static org.actioncontroller.client.HttpURLConnectionApiClient.createTrustStore;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyHttpsServletTest extends AbstractApiClientProxyTest {

    private static final String hostname = "localhost";
    private static final SslContextFactory.Server sslConnectionFactory = new SslContextFactory.Server();
    private static X509Certificate serverCertificate;

    @BeforeClass
    public static void generateCertificates() throws Exception {
        KeyPair serverKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        serverCertificate = generateServerCertificate(serverKeyPair, hostname);

        sslConnectionFactory.setTrustStore(createTrustStore(serverCertificate, "server"));
        sslConnectionFactory.setKeyStore(createKeyStore(hostname, serverKeyPair.getPrivate(), serverCertificate));
    }

    @Override
    @Test
    public void shouldRethrowRuntimeExceptions() {
        expectedLogEvents.expect(
                HttpChannel.class,
                Level.WARN,
                "/test/api/someNiceMath",
                new ArithmeticException("/ by zero")
        );
        assertThatThrownBy(() -> controllerClient.divide(10, 0, false))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("Server Error");
    }

    @Override
    protected ApiClient createClient(TestController controller) throws Exception {

        Server server = new Server();
        ServletContextHandler handler = new ServletContextHandler();
        handler.setSessionHandler(new SessionHandler());
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext().addServlet("testApi", new ApiServlet(controller)).addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);

        ServerConnector connector = new ServerConnector(server, sslConnectionFactory);
        connector.setHost(hostname);
        server.addConnector(connector);
        server.start();

        HttpURLConnectionApiClient apiClient = new HttpURLConnectionApiClient(server.getURI().toString() + "/api");
        apiClient.setTrustedCertificate(serverCertificate);
        return apiClient;
    }

    private X509Certificate generateClientCertificate(PublicKey publicKey, String commonName, PrivateKey signingKey, String issuerDn) {
        try {
            X509CertImpl certificateImpl = new X509CertImpl(createX509Info(
                    "CN=" + commonName,
                    issuerDn,
                    publicKey,
                    Instant.now(),
                    Instant.now().plusSeconds(60*60*24)
            ));
            certificateImpl.sign(signingKey, "SHA512withRSA");
            return certificateImpl;
        } catch (GeneralSecurityException | IOException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    private static X509Certificate generateServerCertificate(KeyPair serverKeyPair, String hostname) {
        try {
            CertificateExtensions extensions = new CertificateExtensions();
            GeneralNames names = new GeneralNames();
            names.add(new GeneralName(new DNSName(hostname)));
            extensions.set(SubjectAlternativeNameExtension.NAME,
                    new SubjectAlternativeNameExtension(names));

            X509CertInfo certInfo = createX509Info(
                    "CN=" + hostname,
                    "CN=" + hostname,
                    serverKeyPair.getPublic(),
                    Instant.now(),
                    Instant.now().plusSeconds(60*60*24));
            certInfo.set(X509CertInfo.EXTENSIONS, extensions);

            X509CertImpl certificateImpl = new X509CertImpl(certInfo);
            certificateImpl.sign(serverKeyPair.getPrivate(), "SHA512withRSA");
            return certificateImpl;
        } catch (GeneralSecurityException | IOException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    private static X509CertInfo createX509Info(String subject, String issuer, PublicKey publicKey, Instant validFrom, Instant validTo) throws CertificateException, IOException {
        X509CertInfo certInfo = new X509CertInfo();
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        certInfo.set(X509CertInfo.VALIDITY, new CertificateValidity(Date.from(validFrom), Date.from(validTo)));
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, new SecureRandom())));
        certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(
                new AlgorithmId(AlgorithmId.sha512WithRSAEncryption_oid)));
        certInfo.set(X509CertInfo.SUBJECT, new X500Name(subject));
        certInfo.set(X509CertInfo.ISSUER, new X500Name(issuer));
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));
        return certInfo;
    }
}
