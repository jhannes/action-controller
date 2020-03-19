package org.actioncontrollerdemo.jdkhttp;

import com.zaxxer.hikari.HikariDataSource;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.config.ConfigValueListener;
import org.actioncontroller.config.PrefixConfigListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class JdkHttpMain {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpMain.class);
    private static DemoServer httpServer;
    private DataSource dataSource;

    public synchronized static void main(String[] args) throws InterruptedException {
        new JdkHttpMain().start();
        JdkHttpMain.class.wait();
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

    private void start() {
        ConfigObserver config = new ConfigObserver("demoserver");
        config.onInetSocketAddress("httpSocketAddress", inetSocketAddress -> {
            DemoServer oldServer = httpServer;
            httpServer = new DemoServer(inetSocketAddress);
            httpServer.start();
            logger.warn("Started on {}", httpServer.getURL());

            if (oldServer != null) {
                oldServer.stop(2);
            }
        }, 20080);

        config.onConfigChange(new DataSourceConfigListener("my.dataSource", ds -> this.dataSource = ds));
    }
}
