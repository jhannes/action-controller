package org.actioncontrollerdemo.jetty;

import org.actioncontroller.config.ConfigObserver;
import org.actioncontrollerdemo.servlet.DemoListener;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private Server server = new Server();
    private ServerConnector connector = new ServerConnector(server);
    private String localhostName;
    private ConfigObserver config = new ConfigObserver(new File("."), "demoserver");

    public Main() throws UnknownHostException {
        localhostName = InetAddress.getLocalHost().getHostAddress();
    }

    public static void main(String[] args) throws Exception {
        new Main().start();
    }

    private void start() throws Exception {
        DemoListener listener = new DemoListener(() -> System.out.println("Hello world"));
        server.setHandler(new MainWebAppContext("/demo", "webapp-actioncontrollerdemo", listener));
        server.start();

        server.addConnector(connector);

        config.onInetSocketAddress("httpSocketAddress", httpSocketAddress -> {
            connector.stop();
            connector.setPort(httpSocketAddress.getPort());
            connector.start();

            logger.warn("Listening on {}", getUrl(connector));
        }, 10080);
    }

    private String getUrl(ServerConnector connector) {
        return "http://" + getHost(connector) + ":" + connector.getPort();
    }

    private String getHost(ServerConnector connector) {
        return connector.getHost() != null ? connector.getHost() : localhostName;
    }

}
