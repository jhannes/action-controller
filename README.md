[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.jhannes.action-controller/action-controller/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.jhannes.action-controller/action-controller)
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
        return "/home/"
    }

}




```


