package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.actioncontrollerdemo.config.ConfigObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.MalformedURLException;

public class JdkHttpMain {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpMain.class);
    private static HttpServer httpServer;
    private DataSource dataSource;

    public static void main(String[] args) throws IOException {
        new JdkHttpMain().start();
    }

    private void start() throws MalformedURLException {
        MainWebHttpHandler handler = new MainWebHttpHandler();

        new Thread(new Runnable() {
            @Override
            public synchronized void run() {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        ConfigObserver config = new ConfigObserver("demoserver");
        config.onInetSocketAddress("httpSocketAddress", inetSocketAddress -> {
            HttpServer oldServer = httpServer;
            httpServer = HttpServer.create();
            httpServer.createContext("/demo", handler);
            httpServer.bind(inetSocketAddress, 0);
            httpServer.start();
            logger.warn("Started on http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort());

            if (oldServer != null) {
                oldServer.stop(2);
            }
        }, 20080);

        config.onPrefix("my.dataSource", cfg -> {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(cfg.get("my.dataSource.jdbcUrl"));
            dataSource.setUsername(cfg.get("my.dataSource.username"));
            dataSource.setPassword(cfg.get("my.dataSource.password"));
            dataSource.getConnection().close();
            logger.info("my.dataSource = {}", dataSource.getJdbcUrl());
            this.dataSource = dataSource;
        });
    }
}
