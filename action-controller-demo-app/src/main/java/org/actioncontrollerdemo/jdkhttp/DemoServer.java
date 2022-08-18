package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.content.ContentSource;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontroller.httpserver.ContentHandler;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;
import org.actioncontrollerdemo.infrastructure.HttpsClientConfiguration;
import org.actioncontrollerdemo.infrastructure.HttpsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Optional;

public class DemoServer {
    private static final Logger logger = LoggerFactory.getLogger(DemoServer.class);

    private HttpServer httpServer;

    private Runnable updater = () -> {
        try {
            doUpdate();
        } catch (IOException e) {
            logger.error("Failed", e);
        }
    };
    private Optional<SSLSocketFactory> clientSslSocketFactory;

    private void doUpdate() throws IOException {
        HttpsURLConnection urlConnection = (HttpsURLConnection) new URL(getUrl(httpsServer)).openConnection();
        clientSslSocketFactory.ifPresent(urlConnection::setSSLSocketFactory);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        urlConnection.getInputStream().transferTo(buffer);
        System.out.println(buffer);
    }

    private final ApiHandler apiHandler = new ApiHandler(new Object[]{
            new TestController(() -> updater.run()),
            new UserController()
    });
    private final ContentHandler swaggerHandler = new ContentHandler(ContentSource.fromWebJar("swagger-ui"));
    private final ContentHandler staticContent = new ContentHandler("/webapp-actioncontrollerdemo/");
    private final RedirectHandler redirectHandler = new RedirectHandler("/demo");
    private HttpsServer httpsServer;

    public void setUpdater(Runnable updater) {
        this.updater = updater;
    }

    public void setServerPort(InetSocketAddress inetSocketAddress) throws IOException {
        if (httpServer != null) {
            stop(1);
        }

        httpServer = HttpServer.create(inetSocketAddress, 0);
        setupServer(httpServer);
        httpServer.start();
        logger.warn("Started {}", getUrl(httpServer));
    }

    private void setHttpsConfiguration(Optional<HttpsConfiguration> httpsConfiguration) throws IOException {
        if (httpsServer != null) {
            httpsServer.stop(1);
            httpsServer = null;
        }

        if (httpsConfiguration.isPresent()) {
            httpsServer = HttpsServer.create(httpsConfiguration.get().getAddress(), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(httpsConfiguration.get().getSSLContext()));
            setupServer(httpsServer);
            httpsServer.start();
            logger.warn("Started {}", getUrl(httpsServer));
        }
    }

    private void setClientSslSocketFactory(Optional<SSLSocketFactory> sslSocketFactory) {
        this.clientSslSocketFactory = sslSocketFactory;
    }

    private void setupServer(HttpServer httpServer) {
        httpServer.createContext("/demo/swagger", swaggerHandler);
        httpServer.createContext("/demo/api", apiHandler)
                .setAuthenticator(new DemoAuthenticator());
        httpServer.createContext("/demo", staticContent);
        httpServer.createContext("/", redirectHandler);
    }

    static String getUrl(HttpServer httpServer) {
        if (httpServer instanceof HttpsServer) {
            return "https://" + httpServer.getAddress().getHostName() + ":" + httpServer.getAddress().getPort();
        } else if (httpServer.getAddress().getPort() != 80) {
            return "http://localhost:" + httpServer.getAddress().getPort();
        } else {
            return "http://localhost";
        }
    }

    public String getURL() {
        return getUrl(httpServer);
    }

    public void setServerPort(int port) throws IOException {
        setServerPort(new InetSocketAddress("localhost", port));
    }

    public void stop(int delay) {
        httpServer.stop(delay);
    }

    public static void main(String[] args) throws InterruptedException {
        DemoServer server = new DemoServer();
        new ConfigObserver("demo-server")
                .onPrefixedValue("https", HttpsConfiguration::create, server::setHttpsConfiguration)
                .onInetSocketAddress("http.address", 8080, server::setServerPort)
                .onPrefixedValue("httpsClient", HttpsClientConfiguration::create, server::setClientSslSocketFactory);
        Thread.currentThread().join();
    }
}
