package org.actioncontrollerdemo.jdkhttp;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

public class WebjarContent extends StaticContent {

    public WebjarContent(String webJarName, String prefix) throws MalformedURLException {
        super(webJarResource(webJarName), prefix);
    }

    static URL webJarResource(String webJarName) {
        String prefix = "/META-INF/resources/webjars/" + webJarName;
        Properties properties = new Properties();
        try (InputStream pomProperties = WebjarContent.class.getResourceAsStream("/META-INF/maven/org.webjars/" + webJarName + "/pom.properties")) {
            properties.load(pomProperties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return WebjarContent.class.getResource(prefix + "/" + properties.get("version"));
    }

}
