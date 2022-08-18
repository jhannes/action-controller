package org.actioncontrollerdemo.infrastructure;

import org.actioncontroller.config.ConfigMap;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

public class HttpsClientConfiguration {
    public static Optional<SSLSocketFactory> create(ConfigMap config) throws Exception {
        return config.mapOptionalFile("keystoreFile", keystoreFile -> createSocketFactory(config, keystoreFile));
    }

    private static SSLSocketFactory createSocketFactory(ConfigMap config, Path keystoreFile) throws GeneralSecurityException, IOException {
        String keyStorePassword = config.getOrDefault("keyStorePassword", "");
        String keyPassword = config.getOrDefault("keyPassword", "");
        List<Path> trustedCertificates = config.listFiles("trustedCertificates", "*-ca.crt");
        Boolean includeSystemCaCerts = config.optional("includeSystemCaCerts").map(Boolean::parseBoolean).orElse(false);

        return SSLFactory.createSslContext(
                SSLFactory.createKeyManagers(keystoreFile, keyStorePassword, keyPassword),
                SSLFactory.createTrustManagers(trustedCertificates, includeSystemCaCerts)
        ).getSocketFactory();
    }
}
