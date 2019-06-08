package org.actioncontrollerdemo.logging;

import org.logevents.LogEvent;
import org.logevents.formatting.ConsoleLogEventFormatter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ConsoleLogFormatter extends ConsoleLogEventFormatter {
    protected final DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]%s: %s\n",
                e.getZonedDateTime().format(timeOnlyFormatter),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(getSimpleCallerLocation(e)),
                mdc(e),
                this.messageFormatter.format(e.getMessage(), e.getArgumentArray()))
                + this.exceptionFormatter.format(e.getThrowable());
    }

    private String mdc(LogEvent e) {
        List<String> mdcValue = new ArrayList<>();
        for (String key : e.getMdcProperties().keySet()) {
            mdcValue.add(key + "=" + e.getMdcProperties().get(key));
        }
        return mdcValue.isEmpty() ? "" : " {" + String.join(", ", mdcValue) + "}";
    }

    private String getSimpleCallerLocation(LogEvent e) {
        StackTraceElement callerLocation = e.getCallerLocation();
        String className = callerLocation.getClassName();
        className = className.substring(className.lastIndexOf(".")+1);
        return className + "." + callerLocation.getMethodName()
                + "(" + callerLocation.getFileName() + ":" + callerLocation.getLineNumber() + ")";
    }
}
