package org.actioncontrollerdemo.jetty;

import org.actioncontroller.config.ConfigObserver;
import org.actioncontrollerdemo.servlet.DemoApplicationListener;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.MovedContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class JettyDemoServer {

    private static final Logger logger = LoggerFactory.getLogger(JettyDemoServer.class);
    private Server server = new Server();
    private ServerConnector connector = new ServerConnector(server);
    private String localhostName;
    private ConfigObserver config = new ConfigObserver("demoserver");

    public JettyDemoServer() throws Exception {
        server.addConnector(connector);
        localhostName = InetAddress.getLocalHost().getHostAddress();
        HandlerList handlers = new HandlerList();
        handlers.addHandler(createServletContext("/demo"));
        handlers.addHandler(new MovedContextHandler(null, "/", "/demo"));
        server.setHandler(handlers);
        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.addBean(mbeanContainer);
        server.start();
    }

    public static void main(String[] args) throws Exception {
        new JettyDemoServer().start();
    }

    private void start() {
        config.onInetSocketAddress("httpSocketAddress", 10080, this::startConnector);
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

    private ServletContextHandler createServletContext(String contextPath) {
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath(contextPath);
        handler.addEventListener(new DemoApplicationListener(() -> System.out.println("Hello world"), new PrincipalFilter()));
        return handler;
    }

    public String getUrl() {
        return getUrl(connector);
    }

    private String getUrl(ServerConnector connector) {
        return "http://" + getHost(connector) + ":" + getPort();
    }

    private String getHost(ServerConnector connector) {
        return connector.getHost() != null ? connector.getHost() : localhostName;
    }

    public int getPort() {
        return connector.getLocalPort();
    }
}
