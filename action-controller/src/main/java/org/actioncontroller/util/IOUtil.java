package org.actioncontroller.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.actioncontroller.util.ExceptionUtil.softenException;

public class IOUtil {

    public static URL asURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw softenException(e);
        }
    }

    public static URI asURI(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw softenException(e);
        }
    }

}
