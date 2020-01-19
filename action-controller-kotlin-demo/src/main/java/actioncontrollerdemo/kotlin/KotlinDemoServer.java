package actioncontrollerdemo.kotlin;

import org.actioncontroller.config.ConfigObserver;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class KotlinDemoServer {

    private static final Logger logger = LoggerFactory.getLogger(KotlinDemoServer.class);
    private Server server = new Server();
    private ServerConnector connector = new ServerConnector(server);
    private String localhostName;
    private ConfigObserver config = new ConfigObserver(new File("."), "demoserver");

    public KotlinDemoServer() throws Exception {
        server.addConnector(connector);
        localhostName = InetAddress.getLocalHost().getHostAddress();
        server.setHandler(createServletContext());
        server.start();
    }

    public static void main(String[] args) throws Exception {
        new KotlinDemoServer().start();
    }

    private void start() {
        config.onInetSocketAddress("httpSocketAddress", this::startConnector, 10080);
    }

    private void startConnector(InetSocketAddress httpSocketAddress) throws Exception {
        startConnector(httpSocketAddress.getPort());
    }

    public void startConnector(int port) throws Exception {
        connector.stop();
        connector.setPort(port);
        connector.start();
        logger.warn("Listening on {}", getUrl(connector));
    }

    private ServletContextHandler createServletContext() {
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/demo");
        handler.addEventListener(new DemoListener(() -> System.out.println("Hello world")));
        return handler;
    }

    public String getUrl() {
        return getUrl(connector);
    }

    private String getUrl(ServerConnector connector) {
        return "http://" + getHost(connector) + ":" + connector.getLocalPort();
    }

    private String getHost(ServerConnector connector) {
        return connector.getHost() != null ? connector.getHost() : localhostName;
    }
}
