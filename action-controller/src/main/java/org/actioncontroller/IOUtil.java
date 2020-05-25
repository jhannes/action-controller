package org.actioncontroller;

import java.net.MalformedURLException;
import java.net.URL;

import static org.actioncontroller.ExceptionUtil.softenException;

public class IOUtil {

    public static URL asURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw softenException(e);
        }
    }

}
