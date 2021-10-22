package org.actioncontrollerdemo;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
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

        URL content = source.resolve("test.txt");
        assertThat(content.openStream())
                .hasContent("Hello World");
    }
    
    @Test
    public void shouldWarnOnInvalidDirectoryName() {
        assertThatThrownBy(() -> ContentSource.fromClasspath(dirResources))
                .hasMessageContaining("Could not find resource " + dirResources);
    }
    
    @Test
    public void shouldBeHappyEvenIfFileNotFound() {
        assertThatThrownBy(() -> source.lastModified(source.resolve("missing.txt")))
                .isInstanceOf(FileNotFoundException.class);
    }
    
    @Test
    public void shouldDetermineMimeType() throws IOException {
        Files.write(dir.resolve("test.txt"), List.of("Hello World"));
        Files.write(dir.resolve("index.html"), List.of("<h1>Hello World</h1>"));
        Files.write(dir.resolve("unknown.ext"), List.of("Garble"));
        
        assertThat(source.getContentType(source.resolve("test.txt")))
                .isEqualTo("text/plain");
        assertThat(source.getContentType(source.resolve("index.html")))
                .isEqualTo("text/html");
        assertThat(source.getContentType(source.resolve("unknown.ext")))
                .isNull();
    }

    @Test
    public void shouldUseIndex() throws IOException {
        Files.createDirectories(dir.resolve("subdir"));
        Files.write(dir.resolve("subdir/index.html"), List.of("<h1>Hello World</h1>"));
        assertThat(source.resolve("subdir/").openStream())
                .hasContent("<h1>Hello World</h1>");
    }
    
    @Test
    public void shouldFindLastModified() throws IOException {
        Files.createDirectories(dir.resolve("subdir"));
        Files.write(dir.resolve("subdir/index.html"), List.of("<h1>Hello World</h1>"));
        Files.setLastModifiedTime(dir.resolve("subdir/index.html"), FileTime.fromMillis(1600000000000L));
        
        assertThat(source.lastModified(source.resolve("subdir/")))
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
        assertThat(source.resolve("/").openStream())
                .hasContent("<h1>Hello World</h1>");
        assertThat(source.lastModified(source.resolve("index.html")))
                .isEqualTo(1600000000000L);
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
        assertThat(source.resolve("index.html").openStream())
                .hasContent("<h1>Hello</h1>");
    }
}