package org.actioncontroller.config;

import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        ConfigMap configMap = new ConfigMap(observer, "apps", Map.of(
                "apps.appOne.clientId", "abc",
                "apps.appOne.clientSecret", "secret",
                "apps.appTwo.clientId", "xyz"
        ));
        assertThat(new ConfigMap(observer, "appOne", configMap).get("clientId")).isEqualTo("abc");
        assertThat(configMap.subMap("appOne").orElseThrow().get("clientId")).isEqualTo("abc");
        assertThat(configMap.listSubMaps()).contains("appOne", "appTwo");
        assertThat(configMap.subMap("missingApp")).isEmpty();

        assertThat(configMap.subMap("appOne").toString()).contains("clientId=abc").contains("prefix=apps.appOne");
        assertThat(configMap.subMap("appTwo").toString()).contains("values={clientId=xyz}");

        assertThat(configMap.subMap("appOne").orElseThrow().getRoot().listSubMaps()).containsExactly("apps");
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

}