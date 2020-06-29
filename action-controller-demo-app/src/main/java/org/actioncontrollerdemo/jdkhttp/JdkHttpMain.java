package org.actioncontrollerdemo.jdkhttp;

import org.actioncontroller.config.ConfigObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdkHttpMain {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpMain.class);
    private static DemoServer httpServer;

    public synchronized static void main(String[] args) throws InterruptedException {
        new JdkHttpMain().start();
        JdkHttpMain.class.wait();
    }

    private void start() {
        ConfigObserver config = new ConfigObserver("demoserver");
        config.onInetSocketAddress("httpSocketAddress", 20080, inetSocketAddress -> {
            DemoServer oldServer = httpServer;
            httpServer = new DemoServer(inetSocketAddress);
            httpServer.start();
            logger.warn("Started on {}", httpServer.getURL());

            if (oldServer != null) {
                oldServer.stop(2);
            }
        });
    }
}
