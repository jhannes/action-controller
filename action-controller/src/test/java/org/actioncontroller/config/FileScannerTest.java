package org.actioncontroller.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class FileScannerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder(new File("target/test"));

    private File directory;

    private List<String> configLines = new ArrayList<>(Arrays.asList(
            "my.dataSource.jdbcUrl=jdbc:datamastery:example",
            "my.dataSource.jdbcUsername=sa",
            "my.dataSource.jdbcPassword="
    ));

    @Test
    public void shouldNotifyWhenWatchedFileIsCreated() throws IOException, InterruptedException {
        directory = temporaryFolder.newFolder();
        BlockingQueue<List<String>> callbacks = new LinkedBlockingQueue<>();
        new FileScanner(directory, Arrays.asList("file-one.txt", "file-two.txt"), callbacks::add);

        writeFile("file-two.txt", configLines);
        List<String> notifiedFiles = callbacks.poll(10, TimeUnit.MILLISECONDS);
        assertThat(notifiedFiles).contains("file-two.txt");
    }

    @Test
    public void shouldNotifyWhenWatchedFileIsUpdated() throws IOException, InterruptedException {
        directory = temporaryFolder.newFolder();
        writeFile("file-one.txt", configLines);
        BlockingQueue<List<String>> callbacks = new LinkedBlockingQueue<>();
        new FileScanner(directory, Arrays.asList("file-one.txt", "file-two.txt"), callbacks::add);

        configLines.add("# A small comment");
        writeFile("file-one.txt", configLines);
        List<String> notifiedFiles = callbacks.poll(10, TimeUnit.MILLISECONDS);
        assertThat(notifiedFiles).contains("file-one.txt");
    }

    @Test
    public void shouldNotifyWhenWatchedFileIsDeleted() throws IOException, InterruptedException {
        directory = temporaryFolder.newFolder();
        writeFile("file-one.txt", configLines);
        BlockingQueue<List<String>> callbacks = new LinkedBlockingQueue<>();
        new FileScanner(directory, Arrays.asList("file-one.txt", "file-two.txt"), callbacks::add);

        new File(directory, "file-one.txt").delete();
        List<String> notifiedFiles = callbacks.poll(10, TimeUnit.MILLISECONDS);
        assertThat(notifiedFiles).contains("file-one.txt");

    }

    @Test
    public void shouldIgnoreWhenUninterestingFileUpdated() throws IOException, InterruptedException {
        directory = temporaryFolder.newFolder();
        writeFile("random-file.txt", configLines);
        BlockingQueue<List<String>> callbacks = new LinkedBlockingQueue<>();
        new FileScanner(directory, Arrays.asList("file-one.txt", "file-two.txt"), callbacks::add);

        List<String> notifiedFiles = callbacks.poll(100, TimeUnit.MILLISECONDS);
        assertThat(notifiedFiles).isNull();
    }

    private Path writeFile(String filename, List<String> configLines) throws IOException {
        return Files.write(new File(directory, filename).toPath(), configLines);
    }
}