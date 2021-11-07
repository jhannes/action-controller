package org.actioncontroller.config;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class FileSystemWatcherTest {

    private final FileSystemWatcher observer = new FileSystemWatcher();
    private final Path testDir = Paths.get("target/test/test-" + UUID.randomUUID() + "/subdir");
    private final SingleItemQueue<String> queue = new SingleItemQueue<>();

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    public FileSystemWatcherTest() throws IOException {
        observer.start();
        Files.createDirectories(testDir);
    }

    @Test
    public void shouldNotifyWhenFileIsCreated() throws IOException, InterruptedException {
        watch("key", testDir, "*.txt");

        queue.assertEmpty();
        Files.write(testDir.resolve("included.txt"), "contents".getBytes());
        assertThat(queue.take()).isEqualTo("key");
        queue.assertEmpty();
    }

    @Test
    public void shouldStopWatcher() throws IOException, InterruptedException {
        watch("key", testDir, "*.txt");
        expectedLogEvents.expectPattern(FileSystemWatcher.class, Level.ERROR, "{} terminated");
        observer.stop();

        Files.write(testDir.resolve("included.txt"), "contents".getBytes());
        queue.assertEmpty();
    }


    @Test
    public void shouldNotifyWhenFileIsModified() throws IOException, InterruptedException {
        Files.write(testDir.resolve("included.txt"), "original".getBytes());
        Files.write(testDir.resolve("excluded.properties"), "content".getBytes());

        watch("key", testDir, "*.txt");
        queue.assertEmpty();

        Files.write(testDir.resolve("included.txt"), "new value".getBytes());
        assertThat(queue.take()).isEqualTo("key");
        queue.assertEmpty();

        Files.write(testDir.resolve("excluded.properties"), "new value".getBytes());
        queue.assertEmpty();
    }

    @Test
    public void shouldNotifyWhenFileIsRenamed() throws IOException, InterruptedException {
        watch("key", testDir, "included.txt");
        Files.write(testDir.resolve("excluded.txt"), "content".getBytes());
        queue.assertEmpty();
        Files.move(testDir.resolve("excluded.txt"), testDir.resolve("included.txt"), StandardCopyOption.ATOMIC_MOVE);
        assertThat(queue.take()).isEqualTo("key");
        queue.assertEmpty();
    }

    @Test
    public void shouldNotifyWhenFileIsDeleted() throws IOException, InterruptedException {
        Files.write(testDir.resolve("included.txt"), "contents".getBytes());
        watch("key", testDir, "*.txt");
        queue.assertEmpty();
        Files.delete(testDir.resolve("included.txt"));
        assertThat(queue.take()).isEqualTo("key");
    }

    @Test
    public void shouldNotNotifyOnUnrelatedFile() throws IOException, InterruptedException {
        watch("key", testDir, "*.txt");

        queue.assertEmpty();
        Files.write(testDir.resolve("excluded.properties"), "contents".getBytes());
        queue.assertEmpty();
    }

    @Test
    public void shouldNotNotifyInWrongDirectory() throws IOException, InterruptedException {
        Path otherDir = Paths.get("target/test/test-" + UUID.randomUUID() + "/subdir");
        Files.createDirectories(otherDir);
        watch("key", testDir, "{included,other}.txt");
        watch("other", otherDir, "*.txt");

        Files.write(testDir.resolve("included.txt"), "content".getBytes());
        assertThat(queue.take()).isEqualTo("key");
        queue.assertEmpty();
    }

    @Test
    public void shouldNotifyWhenDirectoryIsCreated() throws IOException, InterruptedException {
        Path newDirectory = Paths.get("target/test/test-" + UUID.randomUUID() + "/dir/subdir");
        watch("key", newDirectory, "*.txt");

        Files.createDirectories(newDirectory.getParent());
        queue.assertEmpty();
        Files.createDirectory(newDirectory);
        queue.assertEmpty();
        Files.write(newDirectory.resolve("included.txt"), "content".getBytes());
        assertThat(queue.take()).isEqualTo("key");
    }

    @Test
    public void shouldNotifyWhenDirectoryIsRecreated() throws IOException, InterruptedException {
        watch("key", testDir, "*.txt");
        Files.delete(testDir);
        Files.delete(testDir.getParent());
        queue.assertEmpty();
        Files.createDirectories(testDir);
        queue.assertEmpty();

        Files.write(testDir.resolve("included.txt"), "contents".getBytes());
        assertThat(queue.take()).isEqualTo("key");
    }

    @Test
    public void shouldUpdateObserversOnKeyChange() throws IOException, InterruptedException {
        Path otherDir = Paths.get("target/test/test-" + UUID.randomUUID() + "/subdir");
        Files.createDirectories(otherDir);
        watch("key", testDir, "*.txt");
        watch("key", otherDir, "*.txt");

        Files.write(testDir.resolve("included.txt"), "content".getBytes());
        queue.assertEmpty();
        Files.write(otherDir.resolve("included.txt"), "content".getBytes());
        assertThat(queue.take()).isEqualTo("key");
    }

    private static class SingleItemQueue<T> {
        private final ArrayBlockingQueue<T> queue = new ArrayBlockingQueue<>(1);

        public T take() throws InterruptedException {
            T item = queue.poll(100, TimeUnit.MILLISECONDS);
            assertThat(item).as("no item queued").isNotNull();
            return item;
        }

        public void add(T item) {
            assertThat(queue).as("Can't add a second item before the first is taken").isEmpty();
            queue.add(item);
        }

        public void assertEmpty() throws InterruptedException {
            T item = queue.poll(100, TimeUnit.MILLISECONDS);
            assertThat(item).isNull();
        }
    }

    private void watch(String key, Path testDir, String s) {
        observer.watch(key, testDir, testDir.getFileSystem().getPathMatcher("glob:" + s)::matches, queue::add);
    }
}
