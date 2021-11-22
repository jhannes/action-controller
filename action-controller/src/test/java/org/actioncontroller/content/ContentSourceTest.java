package org.actioncontroller.content;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContentSourceTest {

    private final String dirResources = "test-dir/" + UUID.randomUUID();
    private final Path dir = Paths.get("target/test-classes/" + dirResources);
    private ContentSource source;

    @Before
    public void createDirectory() throws IOException {
        Files.createDirectories(dir);
        source = ContentSource.fromClasspath("/" + dirResources);
    }

    @Test
    public void shouldReadFile() throws IOException {
        Files.write(dir.resolve("test.txt"), List.of("Hello World"));

        assertThat(new String(source.getContent("test.txt").readContent()))
                .contains("Hello World");
    }

    @Test
    public void shouldWarnOnInvalidDirectoryName() {
        assertThatThrownBy(() -> ContentSource.fromClasspath(dirResources))
                .hasMessageContaining("Could not find resource " + dirResources);
    }

    @Test
    public void shouldBeHappyEvenIfFileNotFound() throws IOException {
        assertThat(source.getContent("missing.txt")).isNull();
    }

    @Test
    public void shouldDetermineMimeType() throws IOException {
        Files.write(dir.resolve("test.txt"), List.of("Hello World"));
        Files.write(dir.resolve("index.html"), List.of("<h1>Hello World</h1>"));
        Files.write(dir.resolve("unknown.ext"), List.of("Garble"));
        Files.write(dir.resolve("no-extension"), List.of("Garble"));

        assertThat(source.getContent("test.txt").getContentType()).get()
                .isEqualTo("text/plain");
        assertThat(source.getContent("index.html").getContentType()).get()
                .isEqualTo("text/html");
        assertThat(source.getContent("unknown.ext").getContentType()).isEmpty();
        assertThat(source.getContent("no-extension").getContentType()).isEmpty();
    }

    @Test
    public void shouldUseIndex() throws IOException {
        Files.createDirectories(dir.resolve("subdir"));
        Files.write(dir.resolve("subdir/index.html"), List.of("<h1>Hello World</h1>"));
        assertThat(new String(source.getContent("subdir/").readContent()))
                .contains("<h1>Hello World</h1>");
    }

    @Test
    public void shouldUseFallbackFile() throws IOException {
        Files.write(dir.resolve("index.html"), List.of("Hello Root"));
        Files.createDirectories(dir.resolve("default"));
        Files.write(dir.resolve("default/index.html"), List.of("Hello Default"));
        source.withFallbackPath("default/index.html");

        assertThat(new String(source.getContent("/something/else/entirely").readContent()))
                .contains("Hello Default");
        assertThat(source.getContent("/something/else/entirely").getContentType()).get()
                .isEqualTo("text/html");
        assertThat(new String(source.getContent("/").readContent()))
                .contains("Hello Root");
    }

    @Test
    public void shouldReadFromSrcDirectory() throws IOException {
        ContentSource contentSource = ContentSource.fromClasspath("/content-source-test");

        String content = "This is a test: " + UUID.randomUUID();
        Files.write(Paths.get("src/test/resources/content-source-test/test.txt"), List.of(content));
        assertThat(new String(contentSource.getContent("test.txt").readContent()))
                .contains(content);
    }

    @Test
    public void shouldFindLastModified() throws IOException {
        Files.createDirectories(dir.resolve("subdir"));
        Files.write(dir.resolve("subdir/index.html"), List.of("<h1>Hello World</h1>"));
        Files.setLastModifiedTime(dir.resolve("subdir/index.html"), FileTime.fromMillis(1600000000000L));

        assertThat(source.getContent("subdir/").lastModified())
                .isEqualTo(1600000000000L);
    }

    @Test
    public void shouldFindLastModifiedFromJar() throws IOException {
        Path jarFile = Paths.get("target", UUID.randomUUID() + ".jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            JarEntry entry = new JarEntry("foo/index.html");
            jarOutputStream.putNextEntry(entry);
            new ByteArrayInputStream("<h1>Hello World</h1>".getBytes()).transferTo(jarOutputStream);
            jarOutputStream.closeEntry();
        }
        Files.setLastModifiedTime(jarFile, FileTime.fromMillis(1600000000000L));

        ContentSource source = ContentSource.fromURL(new URL("jar:" + jarFile.toFile().toURI() + "!/foo/"));
        source.withFallbackPath("index.html");
        assertThat(new String(source.getContent("/").readContent()))
                .contains("<h1>Hello World</h1>");
        assertThat(source.getContent("/").lastModified())
                .isEqualTo(1600000000000L);
        assertThat(new String(source.getContent("/no/file/here").readContent()))
                .contains("<h1>Hello World</h1>");
    }

    @Test
    public void shouldLoadFromWebJar() throws IOException {
        String webJarName = "webjar-" + UUID.randomUUID();
        String version = "1.2.3-beta2";

        Path webJarProperties = Paths.get("target/test-classes", "/META-INF/maven/org.webjars/" + webJarName + "/pom.properties");
        Files.createDirectories(webJarProperties.getParent());
        Files.write(webJarProperties, ("version=" + version).getBytes());

        Path webJarRoot = Paths.get("target/test-classes", "/META-INF/resources/webjars/" + webJarName + "/" + version);
        Files.createDirectories(webJarRoot);
        Files.write(webJarRoot.resolve("index.html"), "<h1>Hello</h1>".getBytes());

        ContentSource source = ContentSource.fromWebJar(webJarName);
        assertThat(new String(source.getContent("/").readContent()))
                .contains("<h1>Hello</h1>");
    }
}
