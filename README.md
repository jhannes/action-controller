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
    @Override
    public void init() throws ServletException {
        registerController(new MyApiController());
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
            @SessionParameter("userProfile", invalidate=true) Consumer<UserProfile> setUserProfile
    ) {
        // ...
        setUserProfile.apply(newlyLoggedInUser);
        return "/profile";
    }

}
```

The inner workings
------------------

The magic that makes Action Controller work is the annotations like `@PathParam` and `@JsonBody`. 
The set of annotations is actually extensible. Here's how `@RequestParam` is defined:

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

`@HttpParameterMapping` tells Action Controller to use this annotation to resolve the value
of a action method parameter. The `RequestParam.RequestParameterMappingFactory` describes what Action
Controller should do with the annotation. Here's how it's defined:

```java
public class RequestParameterMappingFactory extends HttpRequestParameterMappingFactory<RequestParam> {
        @Override
        public HttpRequestParameterMapping create(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange) -> exchange.getParameter(name, parameter);
        }
}
```

Action Servlet instansiates the mapping factory with a default constructor and invokes create, which lets
the factory set up the mapping with properties from the annotation. The mapper itself takes an
ApiHttpExchange (which encapsulates the HTTP request and the response) and returns the value to use
for the method parameter on the action controller.

That's really all there it to it! :-)


TODO
====

[ ] HttpHeaderRequestMapping
[ ] Custom conversion
[ ] Log payloads

