package org.actioncontroller.util;

public class ExceptionUtil {

    public static RuntimeException softenException(Throwable e) {
        return helper(e);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException helper(Throwable e) throws T {
        throw (T)e;
    }
}
