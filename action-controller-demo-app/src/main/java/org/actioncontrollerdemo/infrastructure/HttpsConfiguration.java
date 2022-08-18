package org.actioncontrollerdemo.infrastructure;

import org.actioncontroller.config.ConfigMap;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

public class HttpsConfiguration {

    public static Optional<HttpsConfiguration> create(ConfigMap config) throws Exception {
        return config.mapOptionalFile("keystoreFile", keystoreFile -> create(config, keystoreFile));
    }

    private static HttpsConfiguration create(ConfigMap config, Path keystoreFile) throws GeneralSecurityException, IOException {
        return new HttpsConfiguration(config, keystoreFile);
    }

    private final Path keystoreFile;
    private final InetSocketAddress address;
    private final SSLContext sslContext;

    public HttpsConfiguration(ConfigMap config, Path keystoreFile) throws GeneralSecurityException, IOException {
        this.address = config.getInetSocketAddress("address", 443);
        this.sslContext = SSLFactory.createSslContext(
                keystoreFile,
                config.getOrDefault("keystorePassword", ""),
                config.getOrDefault("keyPassword", "")
        );
        this.keystoreFile = keystoreFile;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public String toString() {
        return "HttpsConfiguration{keystoreFile=" + keystoreFile + ", address=" + address + '}';
    }
}
