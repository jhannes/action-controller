package org.actioncontroller.config;

import org.actioncontroller.util.ExceptionUtil;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ConfigObserverTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    private final Path directory = createRandomDirectory();

    private Path createRandomDirectory() throws IOException {
        Path dir = Paths.get("target/test/dir-" + UUID.randomUUID());
        Files.createDirectories(dir);
        return dir;
    }

    private final BlockingQueue<Instant> reloadTimes = new ArrayBlockingQueue<>(10);
    private final ConfigObserver observer = new ConfigObserver(directory, "testApp") {
        @Override
        protected void handleConfigurationChanged(Set<String> changedKeys, ConfigMap newConfiguration) {
            super.handleConfigurationChanged(changedKeys, newConfiguration);
            try {
                reloadTimes.put(Instant.now());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private InetSocketAddress httpListenAddress;
    private DummyDataSource dataSource;
    private Duration daemonPollingInterval;

    public ConfigObserverTest() throws IOException {
    }

    @Test
    public void shouldOnlyUpdateWhenPropertyWasChanged() {
        observer.onDurationValue("daemonPollingInterval", null, duration -> this.daemonPollingInterval = duration);

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
    public void shouldUpdateWhenPropertyWasRemoved() {
        AtomicReference<Set<String>> setChangedKeys = new AtomicReference<>();
        ConfigObserver configObserver = new ConfigObserver(directory, "testApp") {
            @Override
            protected void handleConfigurationChanged(Set<String> changedKeys, ConfigMap newConfiguration) {
                super.handleConfigurationChanged(changedKeys, newConfiguration);
                setChangedKeys.set(changedKeys);
            }
        };

        configObserver.updateConfiguration(Map.of("test.one", "value", "test.two", "other"));
        assertThat(setChangedKeys.get()).contains("test.one", "test.two");
        configObserver.updateConfiguration(Map.of("test.one", "value"));
        assertThat(setChangedKeys.get()).contains("test.two").doesNotContain("test.one");
    }

    @Test
    public void shouldListenToInetAddresses() {
        observer.onInetSocketAddress("httpListenAddress",
                10080, address -> this.httpListenAddress = address
        );
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
        observer.onStringValue("prop", "foo,bar", str -> {
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

    @Test
    public void shouldDefaultToEnvironmentVariable() {
        AtomicReference<String> path = new AtomicReference<>();
        observer.onStringValue("path", null, path::set);
        assertThat(path.get()).isEqualTo(System.getenv("PATH"));
        writeConfigLine("path=test");
        assertThat(path.get()).isEqualTo("test");
    }

    @Test
    public void shouldReadUrlValue() throws MalformedURLException {
        AtomicReference<URL> url = new AtomicReference<>();
        observer.onUrlValue("url", new URL("http://example.net"), url::set);
        assertThat(url.get()).isEqualTo(new URL("http://example.net"));
        writeConfigLine("url=https://example.org");
        assertThat(url.get()).isEqualTo(new URL("https://example.org"));
        expectedLogEvents.expectPattern(ConfigObserver.class, Level.ERROR, "Failed to notify listener while reloading {}");
        writeConfigLine("url=this is not a url");
        assertThat(url.get()).isEqualTo(new URL("https://example.org"));
    }


    static class Credentials {
        String username;
        String password;

        Credentials(Map<String, String> config) {
            this(config.get("username"), config.getOrDefault("password", null));
        }

        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    @Test
    public void shouldTransformWithPrefix() {
        AtomicReference<Optional<Credentials>> credentials = new AtomicReference<>(Optional.empty());
        writeConfigLines("credentials.username=someuser");
        observer.onPrefixedOptionalValue("credentials", Credentials::new, credentials::set);
        assertThat(credentials.get()).get().usingRecursiveComparison().isEqualTo(new Credentials("someuser", null));

        writeConfigLines("credentials.username=someuser2", "credentials.password=secret");
        assertThat(credentials.get()).get().usingRecursiveComparison().isEqualTo(new Credentials("someuser2", "secret"));
    }

    @Test
    public void shouldReadOptionalValue() {
        AtomicReference<Optional<Map<String, String>>> credentials = new AtomicReference<>(Optional.empty());
        writeConfigLines("credentials.username=someuser");
        observer.onPrefixedOptionalValue("credentials", credentials::set);
        assertThat(credentials.get()).get().isEqualTo(Map.of("username", "someuser"));
    }

    @Test
    public void shouldValidateRequiredValue() {
        AtomicReference<Credentials> credentials = new AtomicReference<>();
        expectedLogEvents.expectPattern(ConfigObserver.class, Level.ERROR, "Failed to notify listener while reloading {}");
        observer.onPrefixedValue("credentials", Credentials::new, credentials::set);
    }

    @Test
    public void shouldSupportMissingValuesWithPrefix() {
        AtomicReference<Optional<Credentials>> credentials = new AtomicReference<>(Optional.empty());
        writeConfigLines("somethingElse.username=someuser");
        observer.onPrefixedOptionalValue("credentials", Credentials::new, credentials::set);
        assertThat(credentials.get()).isEmpty();

        writeConfigLines("credentials.username=someuser2", "credentials.password=secret");
        assertThat(credentials.get()).get().usingRecursiveComparison().isEqualTo(new Credentials("someuser2", "secret"));

        writeConfigLines("somethingElse.username=someuser");
        assertThat(credentials.get()).isEmpty();
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
        protected DummyDataSource transform(ConfigMap config) {
            return config.optional(prefix + ".jdbcUrl").map(url -> new DummyDataSource(
                    url,
                    config.get(prefix + ".jdbcUsername"),
                    config.getOrDefault(prefix + ".jdbcPassword", "")
            )).orElse(null);
        }

    }

    @Test
    public void shouldWatchForFileChanges() {
        writeConfigLines("my.dataSource.jdbcUrl=jdbc:datamastery:example",
                "my.dataSource.jdbcUsername=sa",
                "my.dataSource.jdbcPassword=");
        observer.onConfigChange(new DummyDataSourceConfigListener(
                "my.dataSource",
                dataSource -> this.dataSource = dataSource
        ));
        assertThat(dataSource).usingRecursiveComparison().isEqualTo(new DummyDataSource(
                "jdbc:datamastery:example", "sa", ""
        ));

        dataSource = null;
        writeConfigLine("otherConfig=something");
        assertThat(dataSource).isNull();

        writeConfigLines("my.dataSource.jdbcUrl=jdbc:datamastery:UPDATED",
                "my.dataSource.jdbcUsername=sa",
                "my.dataSource.jdbcPassword=");
        assertThat(dataSource).usingRecursiveComparison().isEqualTo(new DummyDataSource(
                "jdbc:datamastery:UPDATED", "sa", ""
        ));
    }

    @Test
    public void shouldReadStringList() {
        List<String> list = new ArrayList<>();
        observer.onStringListValue("prop", "foo,bar", l -> {
            list.clear();
            list.addAll(l);
        });
        assertThat(list).containsExactly("foo", "bar");

        writeConfigLine("prop=a,  b ,c");
        assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    public void shouldRecoverFromErrorInListener() {
        expectedLogEvents.expectPattern(ConfigObserver.class, Level.ERROR,
                "Failed to notify listener while reloading {}");
        writeConfigLine("example.number=123");

        String[] fooValue = {null};
        observer.onStringValue("example.foo", null,
                v -> fooValue[0] = v
        );
        observer.onStringValue("example.number", "100", s -> {
            throw new RuntimeException("");
        });

        assertThat(fooValue[0]).isNull();

        writeConfigLines("example.number=one", "example.foo=real value");
        assertThat(fooValue[0]).isEqualTo("real value");
    }

    @Test
    public void shouldReadFile() throws IOException, InterruptedException {
        AtomicReference<String> fileContent = new AtomicReference<>();
        observer.onPrefixedValue(
                "config",
                config -> config.optionalFile("file", observer).map(this::readFile).orElse("<no file>"),
                fileContent::set
        );
        Path file = directory.resolve("file-" + UUID.randomUUID() + ".txt");
        Files.write(file, "This is the file content".getBytes());
        writeConfigLine(("config.file=" + file).replaceAll("\\\\", "/"));
        //waitForFileWatcher();
        Thread.sleep(100);
        assertThat(fileContent).hasValue("This is the file content");
    }

    @Test
    public void shouldWatchForNewFile() throws IOException, InterruptedException {
        Path testDir = Paths.get("target/test/test-" + UUID.randomUUID() + "/subdir");
        Path file = testDir.resolve("file-" + UUID.randomUUID() + ".txt");
        writeConfigLine(("config.file=" + file).replaceAll("\\\\", "/"));

        AtomicReference<String> fileContent = new AtomicReference<>();
        observer.onPrefixedValue(
                "config",
                config -> config.optionalFile("file", observer).map(this::readFile).orElse("<no file>"),
                fileContent::set
        );
        assertThat(fileContent).hasValue("<no file>");

        Files.createDirectories(testDir);
        Thread.sleep(100);
        Files.write(file, "This is the file content".getBytes());
        //waitForFileWatcher();
        Thread.sleep(100);
        assertThat(fileContent).hasValue("This is the file content");
    }

    @Test
    public void shouldWatchForFileChange() throws IOException, InterruptedException {
        Path file = directory.resolve("file-" + UUID.randomUUID() + ".txt");
        writeConfigLine(("config.file=" + file).replaceAll("\\\\", "/"));
        Files.write(file, "Old content".getBytes());

        AtomicReference<String> fileContent = new AtomicReference<>();
        observer.onPrefixedValue(
                "config",
                config -> config.optionalFile("file", observer).map(this::readFile).orElse("<no file>"),
                fileContent::set
        );
        assertThat(fileContent).hasValue("Old content");
        
        Files.write(file, "Updated content".getBytes());
        Thread.sleep(100);
        assertThat(fileContent).hasValue("Updated content");
    }
    
    /*
    @Test
    public void shouldNotWatchOldFile() throws IOException {
        File oldDirectory = new File("target/test/old-dir-" + UUID.randomUUID());
        oldDirectory.mkdirs();
        File oldFile = new File(oldDirectory, "file-" + UUID.randomUUID() + ".txt");
        Files.write(oldFile.toPath(), "Old content".getBytes());
        File newDirectory = new File("target/test/new-dir-" + UUID.randomUUID());
        newDirectory.mkdirs();
        File newFile = new File(newDirectory, "file-" + UUID.randomUUID() + ".txt");
        Files.write(newFile.toPath(), "New content".getBytes());

        writeConfigLine(("config.file=" + oldFile).replaceAll("\\\\", "/"));

        AtomicReference<String> fileContent = new AtomicReference<>();
        AtomicReference<String> otherProperty = new AtomicReference<>();
        observer.onPrefixedValue(
                "config",
                config -> config.optionalFile("file").map(this::readFile).orElse("<no file>"),
                fileContent::set
        ).onStringValue("unrelatedProperty", null, otherProperty::set);
        assertThat(fileContent).hasValue("Old content");

        writeConfigLine(("config.file=" + newFile).replaceAll("\\\\", "/"));
        assertThat(fileContent).hasValue("New content");
        
        fileContent.set("Don't touch");
        Files.write(oldFile.toPath(), "Changes to old file".getBytes());
        Files.write(new File(oldDirectory, "file-" + UUID.randomUUID() + ".txt").toPath(), "Other old dir file".getBytes());
        waitForFileWatcher();
        writeConfigLines(("config.file=" + newFile).replaceAll("\\\\", "/"), "unrelatedProperty=value");
        assertThat(otherProperty).hasValue("value");
        assertThat(fileContent).hasValue("Don't touch");
    }
    
    
    @Test
    public void shouldWatchFilePattern() throws IOException, InterruptedException {
        Path contentDir = Paths.get("target/test/content-dir-" + UUID.randomUUID());

        writeConfigLine(("config.files=" + contentDir + "/*.txt").replaceAll("\\\\", "/"));

        BlockingQueue<Set<String>> file = new ArrayBlockingQueue<>(1);
        observer.onPrefixedValue(
                "config",
                config -> config.listFiles("files").stream().map(this::readFile).collect(Collectors.toSet()),
                file::add
        );
        assertThat(file.poll(100, TimeUnit.MILLISECONDS)).isEqualTo(Set.of());

        Files.createDirectories(contentDir);
        Files.write(contentDir.resolve("fileOne.txt"), "File one".getBytes());
        Files.write(contentDir.resolve("otherFile.ignore"), "Should be ignored".getBytes());

        assertThat(file.poll(100, TimeUnit.MILLISECONDS)).isEqualTo(Set.of("File one"));

        Files.write(contentDir.resolve("fileOne.txt"), "File one updated".getBytes());
        assertThat(file.poll(100, TimeUnit.MILLISECONDS)).isEqualTo(Set.of("File one updated"));

        // For unknown reasons, we don't always get the second file
        Thread.sleep(100);
        file.clear();
        Thread.sleep(100);

        Files.write(contentDir.resolve("fileTwo.txt"), "File two".getBytes());
        assertThat(file.poll(500, TimeUnit.MILLISECONDS)).isEqualTo(Set.of("File one updated", "File two"));

        Files.write(contentDir.resolve("fileTwo.txt"), "File two updated".getBytes());
        assertThat(file.poll(500, TimeUnit.MILLISECONDS)).isEqualTo(Set.of("File one updated", "File two updated"));
    }

     */
    private String readFile(Path file) {
        try {
            return String.join("\n", Files.readAllLines(file));
        } catch (IOException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    private void writeConfigLines(String... lines) {
        reloadTimes.clear();
        try {
            Files.write(directory.resolve("testApp.properties"), Arrays.asList(lines));
        } catch (IOException e) {
            fail("While writing testApp.properties", e);
        }
        waitForFileWatcher();
    }


    private void writeConfigLine(String singleLine) {
        reloadTimes.clear();
        try {
            Files.write(directory.resolve("testApp.properties"), singletonList(singleLine));
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
