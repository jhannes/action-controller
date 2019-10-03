package org.actioncontroller.client;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface ApiClient {
    ApiClientExchange createExchange();

    void setTrustedCertificate(X509Certificate serverCertificate) throws GeneralSecurityException, IOException;

    void addClientKey(PrivateKey privateKey, X509Certificate certificate) throws GeneralSecurityException, IOException;
}
