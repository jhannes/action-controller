package org.actioncontroller.config;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ConfigObserverTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    private File directory = new File("target/test/dir-" + UUID.randomUUID());
    private InetSocketAddress httpListenAddress;
    private DummyDataSource dataSource;
    private BlockingQueue<Instant> reloadTimes = new ArrayBlockingQueue<>(10);
    private ConfigObserver observer = new ConfigObserver(directory, "testApp") {
        @Override
        protected void handleFileChanged(List<String> changedFiles) {
            super.handleFileChanged(changedFiles);
            try {
                reloadTimes.put(Instant.now());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    private Duration daemonPollingInterval;

    @Test
    public void shouldOnlyUpdateWhenPropertyWasChanged() {
        observer.onDuration("daemonPollingInterval", duration -> this.daemonPollingInterval = duration);

        assertThat(daemonPollingInterval).isEqualTo(null);
        daemonPollingInterval = Duration.ofMinutes(5);

        writeConfigLines(
                "example.one=dummy2",
                "example.two=foobar2"
        );
        assertThat(daemonPollingInterval).isEqualTo(Duration.ofMinutes(5));

        writeConfigLine("daemonPollingInterval=PT1M");
        assertThat(daemonPollingInterval).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    public void shouldListenToInetAddresses() {
        observer.onInetSocketAddress("httpListenAddress",
                address -> this.httpListenAddress = address,
                10080);
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress(10080));

        writeConfigLine("httpListenAddress=127.0.0.1:11080");
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress("127.0.0.1", 11080));
        writeConfigLine("httpListenAddress=12080");
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress("0.0.0.0", 12080));
        writeConfigLine("httpListenAddress=:13080");
        assertThat(httpListenAddress).isEqualTo(new InetSocketAddress("0.0.0.0", 13080));
    }

    @Test
    public void shouldReadStringValue() {
        List<String> list = new ArrayList<>();
        observer.onConfigValue("prop", "foo,bar", str -> {
            list.clear();
            list.add(str);
        });
        assertThat(list).containsOnly("foo,bar");

        writeConfigLine("prop=a,  b ,c");
        assertThat(list).containsOnly("a,  b ,c");
    }


    @Test
    public void shouldReadIntValue() {
        AtomicInteger value = new AtomicInteger(0);
        observer.onIntValue("test", 11, value::set);
        assertThat(value.get()).isEqualTo(11);
        writeConfigLine("test = 1337");
        assertThat(value.get()).isEqualTo(1337);
    }

    @Test
    public void shouldReadLongValue() {
        AtomicLong value = new AtomicLong(0);
        observer.onLongValue("test", 11L, value::set);
        assertThat(value.get()).isEqualTo(11L);
        writeConfigLine("test = 1337");
        assertThat(value.get()).isEqualTo(1337L);
    }

    private static class DummyDataSource {

        final String url;
        final String username;
        final String password;

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
    public void shouldWatchForFileChanges() throws IOException {
        List<String> configLines = new ArrayList<>(Arrays.asList(
                "my.dataSource.jdbcUrl=jdbc:datamastery:example",
                "my.dataSource.jdbcUsername=sa",
                "my.dataSource.jdbcPassword="
        ));
        Files.write(new File(directory, "testApp.properties").toPath(), configLines);
        observer.onConfigChange(new DummyDataSourceConfigListener(
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
        observer.onStringList("prop", "foo,bar", l -> {
            list.clear();
            list.addAll(l);
        });
        assertThat(list).containsExactly("foo", "bar");

        writeConfigLine("prop=a,  b ,c");
        assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    public void shouldRecoverFromErrorInListener() {
        expectedLogEvents.expectPattern(ConfigObserver.class, Level.ERROR, "Failed to notify listener {}");
        writeConfigLine("example.number=123");

        String[] fooValue = { null };
        observer.onConfigValue("example.foo", null,
                v -> fooValue[0] = v
        );
        observer.onConfigValue("example.number", "100", s -> { throw new RuntimeException(""); });

        assertThat(fooValue[0]).isNull();

        writeConfigLines("example.number=one", "example.foo=real value");
        assertThat(fooValue[0]).isEqualTo("real value");
    }

    private void writeConfigLines(String... lines) {
        reloadTimes.clear();
        try {
            Files.write(new File(directory, "testApp.properties").toPath(), Arrays.asList(lines));
        } catch (IOException e) {
            fail("While writing testApp.properties", e);
        }
        waitForFileWatcher();
    }


    private void writeConfigLine(String singleLine) {
        reloadTimes.clear();
        try {
            Files.write(new File(directory, "testApp.properties").toPath(), singletonList(singleLine));
        } catch (IOException e) {
            fail("While writing testApp.properties", e);
        }
        waitForFileWatcher();
    }

    private void waitForFileWatcher() {
        try {
            Thread.sleep(10);
            Instant instant = reloadTimes.poll(300, TimeUnit.MILLISECONDS);
            assertThat(instant).describedAs("Timeout on reload wait").isNotNull();
        } catch (InterruptedException e) {
            fail("Thread.sleep interrupted", e);
        }
    }
}