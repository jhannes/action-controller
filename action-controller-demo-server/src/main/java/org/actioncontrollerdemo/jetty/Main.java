package org.actioncontrollerdemo.jetty;

import org.actioncontrollerdemo.config.ConfigObserver;
import org.actioncontrollerdemo.servlet.DemoListener;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private Server server = new Server();
    private ServerConnector connector = new ServerConnector(server);
    private String localhostName;
    private ConfigObserver config = new ConfigObserver("demoserver");

    public Main() throws UnknownHostException {
        localhostName = InetAddress.getLocalHost().getHostAddress();
    }

    public static void main(String[] args) throws Exception {
        new Main().start();
    }

    private void start() throws Exception {
        DemoListener listener = new DemoListener(() -> update());
        server.setHandler(new MainWebAppContext("/demo", "webapp-actioncontrollerdemo", listener));
        server.start();

        server.addConnector(connector);

        config.onInetSocketAddress("httpSocketAddress", httpSocketAddress -> {
            setConnectorPort(httpSocketAddress.getPort());
        }, 10080);
    }

    private void setConnectorPort(int port) throws Exception {
        connector.stop();
        connector.setPort(port);
        connector.start();

        logger.warn("Listening on {}", getUrl(connector));
    }

    private String getUrl(ServerConnector connector) {
        return "http://" + getHost(connector) + ":" + connector.getPort();
    }

    private String getHost(ServerConnector connector) {
        return connector.getHost() != null ? connector.getHost() : localhostName;
    }

    private void update() {
        try {
            setConnectorPort(12080);
        } catch (Exception e) {
            logger.error("While updating port", e);
        }
    }
}
