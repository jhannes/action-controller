[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.jhannes/action-controller/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.jhannes/action-controller)
[![Build Status](https://travis-ci.org/jhannes/action-controller.png)](https://travis-ci.org/jhannes/action-controller)
[![Coverage Status](https://coveralls.io/repos/github/jhannes/action-controller/badge.svg?branch=master)](https://coveralls.io/github/jhannes/action-controller?branch=master)
[![Vulnerability scan](https://snyk.io/test/github/jhannes/action-controller/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/jhannes/action-controller?targetFile=pom.xml)

Action Servlet micro REST framework
===================================

Action Servlet Framework lets you create simple REST Controllers with minimum of magic.

Example API:

```java
public class MyApiServlet extends ApiServlet {

    public MyApiServlet() {
        super(new MyApiController());
    }
}


public class MyApiController {

    @Get("/v1/api/objects")
    @JsonBody
    public List<SomePojo> listObjects(
        @RequestParam("query") Optional<String> query,
        @RequestParam("maxHits") Optional<Integer> maxHits
    ) {
        // ... this is up to you
    }

    @Get("/v1/api/objects/:id")
    @JsonBody
    public SomePojo getObject(@PathParam("id") UUID id) {
        // ... this is up to you
    }

    @Post("/v1/api/objects/")
    @SendRedirect
    public String postData(
        @JsonBody SomePojo myPojo,
        @SessionParameter("user") Optional<User> user
    ) {
        // ... do your thing
        return "/home/";
    }
    
    @Get("/oauth2callback")
    @SendRedirect
    public String establishUserSession(
            @RequestParam("code") String authenticationCode,
            @SessionParameter(value = "userProfile", invalidate=true) Consumer<UserProfile> setUserProfile
    ) {
        // ...
        setUserProfile.apply(newlyLoggedInUser);
        return "/profile";
    }

}
```

The inner workings
------------------

The magic that makes Action Controller work is the annotations like 
[`@PathParam`](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/PathParam.html) and
[`@JsonBody`](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/json/JsonBody.html). 
The set of annotations is actually extensible. Here's how
[`@RequestParam`](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/RequestParam.html) is defined:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(RequestParam.RequestParameterMappingFactory.class)
public @interface RequestParam {

    String value();
}
```

`@Retention` tells javac to keep the information about the annotation available for reflection 
(by default, annotations are only used by the compiler). `@Target` tells javac to only allow
this annotation on method parameters (as opposed to, for example class declarations).

[`@HttpParameterMapping`](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/meta/HttpParameterMapping.html) tells
Action Controller to use this annotation to resolve the value of a action method parameter. The
[`RequestParam.RequestParameterMappingFactory`](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/RequestParam.MapperFactory.html)
describes what Action Controller should do with the annotation. Here's how it's defined:

```java
public class RequestParameterMappingFactory extends HttpRequestParameterMappingFactory<RequestParam> {
        @Override
        public HttpRequestParameterMapping create(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange) -> exchange.getParameter(name, parameter);
        }
}
```

Action Servlet instantiates the mapping factory with a default constructor and invokes create, which lets
the factory set up the mapping with properties from the annotation. The mapper itself takes an
[ApiHttpExchange](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/meta/ApiHttpExchange.html)
(which encapsulates the HTTP request and the response) and returns the value to use for the method parameter
on the action controller.

That's really all there it to it! :-)


### Running with Jetty

```java
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class MyServer {
    public class MyListener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServletContext context = sce.getServletContext();
            context.addServlet("api", new ApiServlet(new MyApiController())).addMapping("/api/*");
        }
    
        @Override
        public void contextDestroyed(ServletContextEvent sce) {    
        }
    }


    public static void main(String[] args) {
        Server server = new Server(8080);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/demo");
        handler.addEventListener(new MyListener());
        server.setHandler(handler);
        server.start();
    }
}
```


### Running with `web.xml`

1. Implemement a `ServletContextListener` which creates `ApiServlet`
2. Add the `ServletContextListener` to your `web.xml` file

```java
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;

public class MyListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        context.addServlet("api", new ApiServlet(new MyApiController())).addMapping("/api/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {    
    }
}
```

```xml
<web-app>
  <listener>
    <listener-class>
       MyListener
    </listener-class>
  </listener>
</web-app>
```


### Running with JDK HttpServer

```java
import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.httpserver.ApiHandler;

public class MyServer {
    public static void main(String[] args){
          HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);
          httpServer.createContext("/demo/api", new ApiHandler(new MyApiController()));
          httpServer.start();
    }
}
```

## ConfigObserver

[ConfigObserver](https://jhannes.github.io/action-controller/apidocs/org/actioncontroller/config/ConfigObserver.html) is a revolutionary way to think of
application configuration. ConfigObserver monitors the configuration values and calls back to you application when
a configuration value you care about is changed. This way, your application can easily hot-reload configuration values.

Example:

```java
public class MyApplication {
    
    private String myConfigValue;
    private DataSource dataSource;
    private ServerSocket serverSocket;

    public MyApplication(ConfigObserver config) {
        config.onConfigValue("myConfigValue", null, v -> this.myConfigValue = v);
        config.onPrefixedValue("dataSource", DataSourceConfig::create, dataSource -> this.dataSouce = dataSource);
        config.onInetSocketAddress("serverAddress",
                address -> {
                    if (serverSocket != null) serverSocket.close();
                    serverSocket = new ServerSocket(address);
                    startServerSocket(serverSocket);
                },
                10080);
    }

    public static void main(String[] args){
      ConfigObserver config = new ConfigObserver(new File("."), "myApp");
      new MyApplication(config);
    }
}
```


TODO
====

* [ ] Log payloads
* [ ] Split HttpClientParameterMapping from HttpParameterMapping?
