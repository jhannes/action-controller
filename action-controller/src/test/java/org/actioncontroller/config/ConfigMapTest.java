package org.actioncontroller.config;

import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigMapTest {

    private final FileListener observer = (key, directory, pathPredicate) -> {};

    @Test
    public void shouldReturnValue() {
        ConfigMap configMap = new ConfigMap(observer, "foo", new ConfigMap(observer, Map.of("foo.bar", "abc")));
        assertThat(configMap.get("bar")).isEqualTo("abc");
        assertThat(configMap.getOrDefault("bar", "something")).isEqualTo("abc");
        assertThat(configMap.getOrDefault("baz", "something")).isEqualTo("something");
    }

    @Test
    public void shouldThrowOnMissingValue() {
        ConfigMap configMap = new ConfigMap(observer, "foo", Map.of("foo.bar", "abc"));
        assertThatThrownBy(() -> configMap.get("baz"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("foo.baz");
        assertThat(configMap.getOrDefault("baz", null)).isNull();
    }

    @Test
    public void shouldThrowOnMissingSubmap() {
        ConfigMap configMap = new ConfigMap(observer, Map.of("foo.bar", "value1", "foo.baz", "value2"));
        assertThat(new ConfigMap(observer, "foo", configMap)).isNotNull();
        assertThatThrownBy(() -> new ConfigMap(observer, "bar", configMap))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Missing key bar");
    }

    @Test
    public void shouldTreatBlankAsMissing() {
        ConfigMap configMap = new ConfigMap(observer, "foo", Map.of("foo.bar", ""));
        assertThat(configMap.getOrDefault("bar", null)).isNull();
        assertThatThrownBy(() -> configMap.get("bar"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("foo.bar");
    }

    @Test
    public void shouldRemoveWhitespace() {
        ConfigMap configMap = new ConfigMap(observer, "foo", Map.of("foo.bar", "   \t  ", "foo.baz", "  true\t"));
        assertThat(configMap.getOrDefault("bar", null)).isNull();
        assertThat(configMap.get("baz")).isEqualTo("true");
        assertThat(configMap.getBoolean("baz")).isTrue();
    }

    @Test
    public void shouldNestConfigMaps() {
        Map<String, String> properties = Map.of(
                "apps.appOne.clientId", "abc",
                "apps.appOne.clientSecret", "secret",
                "apps.appTwo.clientId", "xyz"
        );
        ConfigMap configMap = new ConfigMap(observer, "apps", properties);
        assertThat(new ConfigMap(observer, "appOne", configMap).get("clientId")).isEqualTo("abc");
        assertThat(new ConfigMap(observer, "apps.appOne", properties).get("clientId")).isEqualTo("abc");
        assertThat(new ConfigMap(observer, properties).subMap("apps.appOne").orElseThrow().get("clientId")).isEqualTo("abc");
        assertThat(configMap.subMap("appOne").orElseThrow().get("clientId")).isEqualTo("abc");
        assertThat(configMap.listDirectProperties()).contains("appOne", "appTwo");
        assertThat(configMap.subMap("missingApp")).isEmpty();

        assertThat(configMap.subMap("appOne").toString()).contains("clientId=abc").contains("prefix=apps.appOne");
        assertThat(configMap.subMap("appTwo").toString()).contains("values={clientId=xyz}");

        assertThat(configMap.subMap("appOne").orElseThrow().getRoot().listDirectProperties()).containsExactly("apps");
    }

    private final File directory = new File("target/test/dir-" + UUID.randomUUID());

    @Test
    public void shouldReadConfigFile() throws IOException {
        List<String> lines = Arrays.asList("credentials.username=someuser2", "credentials.password=secret");
        assertThat(directory.mkdirs()).isTrue();
        File file = new File(directory, "testApp.properties");
        Files.write(file.toPath(), lines);

        ConfigMap configuration = ConfigMap.read(observer, file);
        assertThat(configuration).containsEntry("credentials.username", "someuser2");
    }

    @Test
    public void shouldHideSecretsInToString() {
        ConfigMap configMap = new ConfigMap(observer, "apps.appOne", Map.of(
                "apps.appOne.clientId", "abc",
                "apps.appOne.clientSecret", "my-secret",
                "apps.appTwo.clientId", "xyz"
        ));

        assertThat(configMap.toString())
                .contains("clientId=abc")
                .contains("clientSecret=****")
                .doesNotContain("my-secret")
                .doesNotContain("xyz");
    }

    @Test
    public void shouldTrackTouchedProperties() {
        ConfigMap configMap = new ConfigMap(observer, Map.of(
                "appOne.clientId", "abc",
                "appOne.clientSecret", "my-secret",
                "appTwo.clientId", "xyz",
                "appTwo.clientSecret", "xyz"
        ));
        assertThat(configMap.getUntouchedProperties()).containsOnly("appOne", "appTwo");

        ConfigMap subMap = configMap.subMap("appTwo").orElseThrow();
        subMap.get("clientId");
        assertThat(subMap.getUntouchedProperties()).containsExactly("clientSecret");
        subMap.get("clientSecret");
        assertThat(subMap.getUntouchedProperties()).isEmpty();

        assertThat(configMap.getUntouchedProperties()).containsOnly("appOne");
    }

    @Test
    public void shouldGetValuesFromEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("APPS_APPONE_PROP1", "a");
        environment.put("APPS_APPONE_PROP2", "b");
        environment.put("UNRELATED_PROP", "b");

        ConfigMap configMap = new ConfigMap(observer, "apps.appOne", Map.of(), environment);

        assertThat(configMap.get("prop1")).isEqualTo("a");
        assertThat(configMap.toString())
                .contains("APPS_APPONE_PROP1=a")
                .doesNotContain("UNRELATED_PROP");
    }

    @Test
    public void shouldReadFromEnvironment() {
        String environmentVariableWithUnderscore = System.getenv().keySet().stream()
                .filter(s -> s.contains("_"))
                .findFirst()
                .orElseThrow(() -> new AssumptionViolatedException("No environment variable with '_' in this environment"));
        String environment = environmentVariableWithUnderscore.toLowerCase().replace('_', '.');
        String prefix = environment.substring(0, environment.indexOf('.'));
        String key = environment.substring(environment.indexOf('.')+1);

        ConfigMap configMap = new ConfigMap(observer).subMap(prefix).orElseThrow();
        assertThat(configMap.get(key)).isEqualTo(System.getenv(environmentVariableWithUnderscore));
    }

    @Test
    public void shouldReadSubmapsFromEnvironment() {
        String environmentVariableWithUnderscore = System.getenv().keySet().stream()
                .filter(s -> s.length() - s.replace("_", "").length() >= 2)
                .findFirst()
                .orElseThrow(() -> new AssumptionViolatedException("No environment variable with two '_' in this environment"));
        String environment = environmentVariableWithUnderscore.toLowerCase().replace('_', '.');
        int firstPeriod = environment.indexOf('.');
        String prefix1 = environment.substring(0, firstPeriod);
        int secondPeriod = environment.indexOf('.', firstPeriod + 1);
        String prefix2 = environment.substring(firstPeriod+1, secondPeriod);
        String key = environment.substring(secondPeriod +1);

        ConfigMap configMap = new ConfigMap(observer).subMap(prefix1).orElseThrow().subMap(prefix2).orElseThrow();
        assertThat(configMap.get(key)).isEqualTo(System.getenv(environmentVariableWithUnderscore));
    }

    @Test
    public void shouldReadInetSocketAddress() {
        ConfigMap configMap = new ConfigMap(observer, "httpListenAddress", Map.of(
                "httpListenAddress.one", "127.0.0.1:11080",
                "httpListenAddress.two", "12080",
                "httpListenAddress.three", ":13080"
        ));

        assertThat(configMap.getInetSocketAddress("one", 10080))
                .isEqualTo(new InetSocketAddress("127.0.0.1", 11080));
        assertThat(configMap.getInetSocketAddress("two", 10080))
                .isEqualTo(InetSocketAddress.createUnresolved("localhost", 12080));
        assertThat(configMap.getInetSocketAddress("three", 10080))
                .isEqualTo(InetSocketAddress.createUnresolved("localhost", 13080));
    }

    @Test
    public void shouldReadDuration() {
        ConfigMap configMap = new ConfigMap(observer, "testConfig", Map.of(
                "testConfig.one", "PT1M",
                "testConfig.two", "PT2H",
                "testConfig.wrong", "Not a duration"
        ));

        assertThat(configMap.getDuration("one", Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofMinutes(1));
        assertThat(configMap.getDuration("two", Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofHours(2));
        assertThat(configMap.getDuration("three", Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofSeconds(1));
        assertThatThrownBy(() -> configMap.getDuration("wrong", Duration.ofSeconds(1)))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    public void shouldReadPeriod() {
        ConfigMap configMap = new ConfigMap(observer, "testConfig", Map.of(
                "testConfig.one", "P1M",
                "testConfig.two", "P2Y",
                "testConfig.wrong", "Not a period"
        ));

        assertThat(configMap.getPeriod("one", Period.ofDays(1)))
                .isEqualTo(Period.ofMonths(1));
        assertThat(configMap.getPeriod("two", Period.ofDays(1)))
                .isEqualTo(Period.ofYears(2));
        assertThat(configMap.getPeriod("three", Period.ofDays(1)))
                .isEqualTo(Period.ofDays(1));
        assertThatThrownBy(() -> configMap.getPeriod("wrong", Period.ofDays(1)))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    public void shouldGetRegularFile() throws IOException {
        assertThatThrownBy(() -> new ConfigMap(observer, Map.of()).getRegularFile("testFile"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("Missing config value testFile");

        Path testDir = Paths.get("target/test/test-" + UUID.randomUUID() + "/subdir");
        Path file = testDir.resolve("file.txt");
        assertThatThrownBy(() -> new ConfigMap(observer, Map.of("testFile", file.toString())).getRegularFile("testFile"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("File not found: testFile=" + file);

        Files.createDirectories(testDir);
        assertThatThrownBy(() -> new ConfigMap(observer, Map.of("testFile", testDir.toString())).getRegularFile("testFile"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("Not a regular file: testFile=" + testDir);

        Files.write(file, List.of("First line"));
        assertThat(new ConfigMap(observer, Map.of("testFile", file.toString())).getRegularFile("testFile"))
                .isEqualTo(file);
    }
}
