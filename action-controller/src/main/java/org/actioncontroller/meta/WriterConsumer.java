package org.actioncontroller.meta;

import java.io.IOException;
import java.io.PrintWriter;

@FunctionalInterface
public interface WriterConsumer {

    void accept(PrintWriter writer) throws IOException;

}
