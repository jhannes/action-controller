package org.actioncontrollerdemo.jdkhttp;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SSLFactory {
    
    public static KeyManager[] createKeyManagers(
            Path keystoreFile, String keyStorePassword, String keyPassword
    ) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        try (InputStream input = Files.newInputStream(keystoreFile)) {
            keyStore.load(input, keyStorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    public static SSLContext createSslContext(Path keystoreFile, String keyStorePassword, String keyPassword) throws GeneralSecurityException, IOException {
        return createSslContext(createKeyManagers(keystoreFile, keyStorePassword, keyPassword), null);
    }

    public static SSLContext createSslContext(KeyManager[] keyManagers, TrustManager[] trustManagers) throws GeneralSecurityException  {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }

    public static TrustManager[] createTrustManagers(List<Path> trustedCertificates, boolean includeSystemCaCerts)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException
    {
        TrustManager[] trustManagers = createTrustManagers(trustedCertificates);
        return includeSystemCaCerts ? createTrustManagersWithDefault(trustManagers) : trustManagers;
    }

    private static TrustManager combinedTrustManager(X509TrustManager trustManager, X509TrustManager defaultTrustManager) {
        List<X509Certificate> acceptedIssuers = new ArrayList<>();
        acceptedIssuers.addAll(Arrays.asList(defaultTrustManager.getAcceptedIssuers()));
        acceptedIssuers.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
        X509Certificate[] acceptedIssuersArray = acceptedIssuers.toArray(new X509Certificate[0]);

        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                try {
                    defaultTrustManager.checkClientTrusted(x509Certificates, s);
                } catch (CertificateException e) {
                    trustManager.checkClientTrusted(x509Certificates, s);
                }
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                try {
                    defaultTrustManager.checkServerTrusted(x509Certificates, s);
                } catch (CertificateException e) {
                    trustManager.checkServerTrusted(x509Certificates, s);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return acceptedIssuersArray;
            }
        };
    }

    private static TrustManager[] createTrustManagersWithDefault(TrustManager[] trustManagers) throws NoSuchAlgorithmException {
        TrustManager[] defaultTrustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).getTrustManagers();
        return new TrustManager[] {
                combinedTrustManager(getX509TrustManager(trustManagers), getX509TrustManager(defaultTrustManagers))
        };
    }

    private static X509TrustManager getX509TrustManager(TrustManager[] trustManagers) {
        return (X509TrustManager) Stream.of(trustManagers)
                .filter(t -> t instanceof X509TrustManager)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No X509TrustManager found"));
    }

    private static TrustManager[] createTrustManagers(List<Path> trustedCertificates)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException
    {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
        KeyStore trustStore = KeyStore.getInstance("pkcs12");
        trustStore.load(null, null);
        for (Path certificate : trustedCertificates) {
            try (InputStream input = Files.newInputStream(certificate)) {
                trustStore.setCertificateEntry(certificate.getFileName().toString(), certificateFactory.generateCertificate(input));
            }
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

}
