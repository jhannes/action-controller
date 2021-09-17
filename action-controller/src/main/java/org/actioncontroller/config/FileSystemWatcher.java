package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileSystemWatcher {
    
    @FunctionalInterface
    interface FileSystemObserver {
        void apply(String key) throws Exception;
    }

    private static class FileObserver {
        private final Path directory;
        private final FileSystemObserver observer;
        private final Predicate<Path> pathPredicate;
        private final Path finalDirectory;
        private final Predicate<Path> finalPathPredicate;

        public Path getDirectory() {
            return directory;
        }

        public FileObserver(Path directory, Predicate<Path> pathPredicate, FileSystemObserver observer, Path finalDirectory, Predicate<Path> finalPathPredicate) {
            this.directory = directory;
            this.observer = observer;
            this.pathPredicate = pathPredicate;
            this.finalDirectory = finalDirectory;
            this.finalPathPredicate = finalPathPredicate;
        }

        public void apply(Path updatedDirectory, Set<Path> changedFiles, String key) throws Exception {
            if (updatedDirectory.equals(directory) && changedFiles.stream().anyMatch(pathPredicate)) {
                observer.apply(key);
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(FileSystemWatcher.class);

    private final WatchService watchService;
    private final Thread thread;
    private final Map<String, FileObserver> observers = new HashMap<>();
    private final Map<Path, WatchKey> directoryKeys = new HashMap<>();

    public FileSystemWatcher() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.thread = new Thread(this::run);
        thread.setName(this + "-Watcher");
        thread.setDaemon(true);
    }
    
    public void start() {
        thread.start();
    }

    public void watch(String key, Path directory, Predicate<Path> pathPredicate, FileSystemObserver observer) throws IOException {
        if (!Files.isDirectory(directory)) {
            Path missingDirectory = directory;
            while (!Files.isDirectory(missingDirectory.getParent())) {
                missingDirectory = missingDirectory.getParent();
            }
            WatchKey watchKey = missingDirectory.getParent().register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            directoryKeys.put(missingDirectory.getParent(), watchKey);
            Path fileName = missingDirectory.getFileName();
            observers.put(key, new FileObserver(missingDirectory.getParent(), f -> f.equals(fileName), k -> watch(k, directory, pathPredicate, observer), directory, pathPredicate));
            return;
        }
        WatchKey watchKey = directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        directoryKeys.put(directory, watchKey);
        FileObserver oldObserver = observers.put(key, new FileObserver(directory, pathPredicate, observer, directory, pathPredicate));
        if (oldObserver != null) {
            if (observers.values().stream().noneMatch(o -> o.getDirectory().equals(oldObserver.getDirectory()))) {
                directoryKeys.get(oldObserver.getDirectory()).cancel();
            }
        }
    }

    private void run() {
        try {
            while (!Thread.interrupted()) {
                loop();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.error("{} terminated", this);
    }

    private void loop() throws InterruptedException {
        WatchKey key = watchService.take();
        Thread.sleep(10);
        Path keyDirectory = (Path) key.watchable();
        List<WatchEvent<?>> events = key.pollEvents();
        Set<Path> files = events.stream()
                .map(e -> (Path) e.context())
                .collect(Collectors.toSet());
        key.reset();
        
        if (!Files.exists(keyDirectory)) {
            logger.debug("Directory {} removed. Registering directory listeners", keyDirectory);
            for (Map.Entry<String, FileObserver> observer : new HashSet<>(this.observers.entrySet())) {
                FileObserver o = observer.getValue();
                if (o.getDirectory().startsWith(keyDirectory)) {
                    try {
                        logger.debug("Registering directory listener for {}", observer.getKey());
                        watch(observer.getKey(), o.finalDirectory, o.finalPathPredicate, o.observer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            logger.debug("Notifying observers dir={} files={}", keyDirectory, files);
            for (Map.Entry<String, FileObserver> observer : observers.entrySet()) {
                try {
                    observer.getValue().apply(keyDirectory, files, observer.getKey());
                } catch (Exception e) {
                    logger.error("Failed to notify {}", observer.getValue(), e);
                }
            }
        }
    }

    public void stop() {
        thread.interrupt();
    }

}
