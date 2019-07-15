package org.actioncontroller.config;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// TODO: Test exception

// TODO: InetSocketAddress as number, as :number

// TODO: Exception in listener

public class ConfigObserverTest {

    private File directory = new File("target/test/dir-" + UUID.randomUUID());
    private InetSocketAddress httpListenAddress;
    private DummyDataSource dataSource;
    private ConfigObserver observer = new ConfigObserver(directory, "testApp");
    private Duration daemonPollingInterval;

    @Test
    public void shouldOnlyUpdateWhenPropertyWasChanged() throws IOException, InterruptedException {
        observer.onDuration("daemonPollingInterval", duration -> this.daemonPollingInterval = duration);

        assertThat(daemonPollingInterval).isEqualTo(null);
        daemonPollingInterval = Duration.ofMinutes(5);

        Files.write(new File(directory, "testApp.properties").toPath(), Arrays.asList(
                "example.one=dummy2",
                "example.two=foobar2"
        ));
        Thread.sleep(100);
        assertThat(daemonPollingInterval).isEqualTo(Duration.ofMinutes(5));

        Files.write(new File(directory, "testApp.properties").toPath(), Arrays.asList(
                "daemonPollingInterval=PT1M"
        ));
        Thread.sleep(100);
        assertThat(daemonPollingInterval).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    public void acceptanceTest() throws IOException, InterruptedException {
        observer.onInetSocketAddress("httpListenAddress",
                address -> this.httpListenAddress = address,
                10080);
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress(10080));

        Files.write(new File(directory, "testApp.properties").toPath(),
                Arrays.asList("httpListenAddress=127.0.0.1:11080"));
        Thread.sleep(100);
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress("127.0.0.1", 11080));
    }

    @Test
    public void shouldReadStringValue() {
        List<String> list = new ArrayList<>();
        observer.onConfigValue("prop", "foo,bar", str -> {
            list.clear();
            list.add(str);
        });
        assertThat(list).containsOnly("foo,bar");

        writeConfigFile("prop=a,  b ,c");
        waitForFileWatcher();
        assertThat(list).containsOnly("a,  b ,c");
    }


    @Test
    public void shouldReadIntValue() {
        AtomicInteger value = new AtomicInteger(0);
        observer.onIntValue("test", 11, value::set);
        assertThat(value.get()).isEqualTo(11);
        writeConfigFile("test = 1337");
        waitForFileWatcher();
        assertThat(value.get()).isEqualTo(1337);
    }

    @Test
    public void shouldReadLongValue() {
        AtomicLong value = new AtomicLong(0);
        observer.onLongValue("test", 11L, value::set);
        assertThat(value.get()).isEqualTo(11L);
        writeConfigFile("test = 1337");
        waitForFileWatcher();
        assertThat(value.get()).isEqualTo(1337L);
    }




    private static class DummyDataSource {

        private final String url;
        private final String username;
        private final String password;

        public DummyDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }
    }

    private static class DummyDataSourceConfigListener extends PrefixConfigListener<DummyDataSource> {

        private DummyDataSourceConfigListener(String prefix, ConfigValueListener<DummyDataSource> listener) {
            super(prefix, listener);
        }

        @Override
        protected DummyDataSource transform(Map<String, String> config) {
            return new DummyDataSource(
                    config.get(prefix + ".jdbcUrl"),
                    config.get(prefix + ".jdbcUsername"),
                    config.get(prefix + ".jdbcPassword")
            );
        }

    }

    @Test
    public void otherAcceptanceTest() throws IOException {
        List<String> configLines = new ArrayList<>(Arrays.asList(
                "my.dataSource.jdbcUrl=jdbc:datamastery:example",
                "my.dataSource.jdbcUsername=sa",
                "my.dataSource.jdbcPassword="
        ));
        Files.write(new File(directory, "testApp.properties").toPath(), configLines);
        ConfigObserver configObserver = new ConfigObserver(directory, "testApp");
        configObserver.onConfigChange(new DummyDataSourceConfigListener(
                "my.dataSource",
                dataSource -> this.dataSource = dataSource
        ));
        assertThat(dataSource).isEqualToComparingFieldByField(new DummyDataSource(
                "jdbc:datamastery:example", "sa", ""
        ));

        dataSource = null;
        configLines.add("otherConfig=something");
        Files.write(new File(directory, "testApp.properties").toPath(), configLines);
        waitForFileWatcher();
        assertThat(dataSource).isNull();

        configLines.set(0, "my.dataSource.jdbcUrl=jdbc:datamastery:UPDATED");
        Files.write(new File(directory, "testApp.properties").toPath(), configLines);
        waitForFileWatcher();
        assertThat(dataSource).isEqualToComparingFieldByField(new DummyDataSource(
                "jdbc:datamastery:UPDATED", "sa", ""
        ));
    }


    @Test
    public void shouldReadStringList() {
        List<String> list = new ArrayList<>();
        ConfigObserver configObserver = new ConfigObserver(directory, "testApp");
        configObserver.onStringList("prop", "foo,bar", l -> {
            list.clear();
            list.addAll(l);
        });
        assertThat(list).containsExactly("foo", "bar");

        writeConfigFile("prop=a,  b ,c");
        waitForFileWatcher();
        assertThat(list).containsExactly("a", "b", "c");
    }

    private void writeConfigFile(String singleLine) {
        try {
            Files.write(new File(directory, "testApp.properties").toPath(), singletonList(singleLine));
        } catch (IOException e) {
            fail("While writing testApp.properties", e);
        }
    }

    private void waitForFileWatcher() {
        // TODO: It would be great to connect this to the actual file watcher
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            fail("Thread.sleep interrupted", e);
        }
    }
}