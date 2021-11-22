package org.actioncontroller.config;

import java.nio.file.Path;
import java.util.function.Predicate;

public interface FileListener {
    void listenToFileChange(String key, Path directory, Predicate<Path> pathPredicate);
}
