package org.actioncontroller.meta;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface OutputStreamConsumer {
    void accept(OutputStream output) throws IOException;
}
