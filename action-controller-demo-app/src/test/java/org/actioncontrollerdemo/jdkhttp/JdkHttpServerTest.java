package org.actioncontrollerdemo.jdkhttp;

import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.IOException;

public class JdkHttpServerTest {

    @Test
    @Ignore
    public void shouldDisplayHomepageInBrowser() throws IOException {
        DemoServer server = new DemoServer(0);
        server.start();

        ChromeDriver driver = new ChromeDriver();
        System.out.println(server.getURL());
        driver.get(server.getURL() + "/api/test");
        String pageSource = driver.getPageSource();
        System.out.println(pageSource);
    }

}
