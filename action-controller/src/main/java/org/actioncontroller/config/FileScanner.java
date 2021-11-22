package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileScanner {
    private static final Logger logger = LoggerFactory.getLogger(FileScanner.class);
    private final Path directory;

    public FileScanner(Path directory, List<String> fileNames, Consumer<List<String>> fileChangeListener)  {
        this.directory = directory;
        WatchService watchService = newWatchService(directory);
        Thread configurationWatcher = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = watchService.take();
                    List<String> changedFiles = key.pollEvents().stream()
                            .map(e -> ((Path) e.context()).getFileName().toString())
                            .filter(fileNames::contains)
                            .collect(Collectors.toList());
                    key.reset();
                    if (!changedFiles.isEmpty()) {
                        logger.debug("Files changed: {}", changedFiles);
                        fileChangeListener.accept(changedFiles);
                    }
                }
            } catch (InterruptedException ignored) {
            }
        });
        configurationWatcher.setName(this + "-Watcher");
        configurationWatcher.setDaemon(true);
        configurationWatcher.start();
        logger.debug("Started {}", configurationWatcher);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{directory=" +  directory.toAbsolutePath() + "}";
    }

    private WatchService newWatchService(Path directory) {
        try {
            WatchService watchService = directory.getFileSystem().newWatchService();
            directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            return watchService;
        } catch (IOException e) {
            throw new ConfigException("Failed to initialize FileScanner", e);
        }
    }
}
