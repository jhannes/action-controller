package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.content.ContentSource;
import org.actioncontroller.httpserver.ContentHandler;
import org.actioncontrollerdemo.infrastructure.HttpsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.actioncontrollerdemo.jdkhttp.DemoServer.getUrl;

public class DemoRelyingServer {
    private static final Logger logger = LoggerFactory.getLogger(DemoRelyingServer.class);
    private HttpServer httpServer;

    public static void main(String[] args) throws InterruptedException {
        new DemoRelyingServer().start();
    }

    private final ContentHandler staticContent = new ContentHandler(ContentSource.fromClasspath("/webapp-relying-server/"));
    private HttpsServer httpsServer;

    private void start() throws InterruptedException {
        new ConfigObserver("demo-relying")
                .onInetSocketAddress("http.address", this::setHttpAddress)
                .onPrefixedValue("https", HttpsConfiguration::create, this::setHttpsConfiguration);
        Thread.currentThread().join();
    }

    private void setHttpAddress(Optional<InetSocketAddress> httpAddress) throws IOException {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
        if (httpAddress.isPresent()) {
            httpServer = HttpServer.create(httpAddress.get(), 0);
            setupServer(httpServer);
            httpServer.start();
            logger.warn("Started {}", getUrl(httpServer));
        } else {
            logger.warn("HTTP not started");
        }
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
        } else {
            logger.warn("Not configured");
        }
    }

    private void setupServer(HttpServer httpServer) {
        httpServer.createContext("/", staticContent);
    }

}
