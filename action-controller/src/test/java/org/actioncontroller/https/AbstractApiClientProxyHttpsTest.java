package org.actioncontroller.https;

import org.actioncontroller.ClientCertificate;
import org.actioncontroller.ContentBody;
import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.GET;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.client.HttpClientException;
import org.junit.Before;
import org.junit.Test;
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractApiClientProxyHttpsTest {

    public static class CertificateController {
        @GET("/subject")
        @ContentBody
        public String getSubject(@ClientCertificate X509Certificate certificate) {
            return certificate.getSubjectDN().getName();
        }

        @GET("/optionalSubject")
        @ContentBody
        public String getOptionalSubject(@ClientCertificate Optional<X509Certificate> certificate) {
            return certificate.map(c -> c.getSubjectDN().getName()).orElse("<none>");
        }
    }

    @Test
    public void shouldPickCorrectCertificate() throws GeneralSecurityException, IOException {
        apiClient.addClientKey(serverKeyPair.getPrivate(), serverCertificate);
        apiClient.addClientKey(clientKeyPair.getPrivate(), clientCertificate);
        ApiClientProxyHttpsServletTest.CertificateController clientProxy = ApiClientProxy.create(ApiClientProxyHttpsServletTest.CertificateController.class, apiClient);

        String subject = clientProxy.getSubject(clientCertificate);
        assertThat(subject).isEqualTo(clientCertificate.getSubjectDN().getName());
    }

    @Test
    public void shouldFailForMissingRequiredCertificate() throws GeneralSecurityException, IOException {
        apiClient.addClientKey(clientKeyPair.getPrivate(), clientCertificate);
        ApiClientProxyHttpsServletTest.CertificateController clientProxy = ApiClientProxy.create(ApiClientProxyHttpsServletTest.CertificateController.class, apiClient);
        assertThatThrownBy(() -> clientProxy.getSubject(null))
            .isEqualTo(new HttpClientException(401, "Missing client certificate"));
    }

    @Test
    public void shouldAbortIfMissingCertificate() throws GeneralSecurityException, IOException {
        apiClient.addClientKey(serverKeyPair.getPrivate(), serverCertificate);
        ApiClientProxyHttpsServletTest.CertificateController clientProxy = ApiClientProxy.create(ApiClientProxyHttpsServletTest.CertificateController.class, apiClient);

        assertThatThrownBy(() -> clientProxy.getSubject(clientCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(clientCertificate.getSubjectDN().getName())
                .hasMessageContaining("not find key for ");
    }

    @Test
    public void shouldUseOptionalCertificate() throws GeneralSecurityException, IOException {
        apiClient.addClientKey(clientKeyPair.getPrivate(), clientCertificate);
        ApiClientProxyHttpsServletTest.CertificateController clientProxy = ApiClientProxy.create(ApiClientProxyHttpsServletTest.CertificateController.class, apiClient);
        String subject = clientProxy.getOptionalSubject(Optional.ofNullable(clientCertificate));
        assertThat(subject).isEqualTo(clientCertificate.getSubjectDN().getName());
    }

    @Test
    public void shouldUseAllowMissingOptionalCertificate() throws GeneralSecurityException, IOException {
        apiClient.addClientKey(clientKeyPair.getPrivate(), clientCertificate);
        ApiClientProxyHttpsServletTest.CertificateController clientProxy = ApiClientProxy.create(ApiClientProxyHttpsServletTest.CertificateController.class, apiClient);
        String subject = clientProxy.getOptionalSubject(Optional.empty());
        assertThat(subject).isEqualTo("<none>");
    }

    @Before
    public void createKeys() throws NoSuchAlgorithmException {
        serverKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        serverCertificate = generateServerCertificate(serverKeyPair, hostname);

        clientKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        clientCertificate = generateClientCertificate(clientKeyPair.getPublic(), UUID.randomUUID().toString(), serverKeyPair.getPrivate(), serverCertificate.getSubjectDN().getName());
    }

    protected X509Certificate serverCertificate;
    protected KeyPair serverKeyPair;
    protected String hostname = "localhost";
    protected ApiClient apiClient;
    private X509Certificate clientCertificate;
    private KeyPair clientKeyPair;

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

    private X509Certificate generateServerCertificate(KeyPair serverKeyPair, String hostname) {
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

    private X509CertInfo createX509Info(String subject, String issuer, PublicKey publicKey, Instant validFrom, Instant validTo) throws CertificateException, IOException {
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
