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
@HttpParameterMapping(RequestParam.RequestParameterMapping.class)
public @interface RequestParam {

    String value();
}
```

`@Retention` tells javac to keep the information about the annotation available for reflection 
(by default, annotations are only used by the compiler). `@Target` tells javac to only allow
this annotation on method parameters (as opposed to, for example class declarations).

`@HttpParameterMapping` tells Action Controller to use this annotation to resolve the value
of a action method parameter. The `RequestParam.RequestParameterMapping` describes what Action
Controller should do with the annotation. Here's how it's defined:

```java
public class RequestParameterMapping extends AbstractHttpRequestParameterMapping {
    private String value;

    public RequestParameterMapping(RequestParam reqParam, Parameter parameter) {
        super(parameter);
        value = reqParam.value();
    }

    @Override
    public Object apply(HttpServletRequest req, Map<String, String> pathParams) {
        return convertToParameterType(req.getParameter(value), value);
    }
}
```

Action Servlet automatically searches for a constructor with the `RequestParam` (that is,
the annotation itself), which lets the mapping class use annotation properties to do its job.

That's really all there it to it! :-)

