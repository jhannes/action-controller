package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.config.ConfigValueListener;
import org.actioncontroller.config.PrefixConfigListener;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontrollerdemo.TestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.Map;

public class JdkHttpMain {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpMain.class);
    private static HttpServer httpServer;
    private DataSource dataSource;

    public static void main(String[] args) throws IOException {
        new JdkHttpMain().start();
    }

    private static class DataSourceConfigListener extends PrefixConfigListener<DataSource> {

        private DataSourceConfigListener(String prefix, ConfigValueListener<DataSource> listener) {
            super(prefix, listener);
        }

        @Override
        protected DataSource transform(Map<String, String> cfg) throws SQLException {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(cfg.get(prefix + ".jdbcUrl"));
            dataSource.setUsername(cfg.get(prefix + ".jdbcUsername"));
            dataSource.setPassword(cfg.get(prefix + ".jdbcPassword"));
            dataSource.getConnection().close();
            logger.info("{} = {}", prefix, dataSource.getJdbcUrl());

            return dataSource;
        }
    }

    private void start() throws MalformedURLException {
        StaticContent webJar = StaticContent.createWebJar("swagger-ui", "/demo/swagger");
        StaticContent staticContent = new StaticContent(getClass().getResource("/webapp-actioncontrollerdemo"), "/demo");
        ApiHandler apiHandler = new ApiHandler("/demo", "/api", new TestController(() -> {
            System.out.println("Hello");
        }));

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

        ConfigObserver config = new ConfigObserver(new File("."), "demoserver");
        config.onInetSocketAddress("httpSocketAddress", inetSocketAddress -> {
            HttpServer oldServer = httpServer;
            httpServer = HttpServer.create();
            httpServer.createContext("/demo/swagger", webJar);
            httpServer.createContext("/demo/api", apiHandler);
            httpServer.createContext("/demo", staticContent);
            httpServer.createContext("/", new RedirectHandler("/demo"));
            httpServer.bind(inetSocketAddress, 0);
            httpServer.start();
            logger.warn("Started on http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort());

            if (oldServer != null) {
                oldServer.stop(2);
            }
        }, 20080);


        config.onConfigChange(new DataSourceConfigListener("my.dataSource", ds -> this.dataSource = ds));
    }
}
