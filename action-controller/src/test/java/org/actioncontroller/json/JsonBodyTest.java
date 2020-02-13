package org.actioncontroller.json;

import org.actioncontroller.GET;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Test;

import javax.servlet.ServletException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonBodyTest {

    public static class Person {
        private String firstName, lastName;

        public Person() {
        }

        public Person(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

    }

    public static class TestController {
        @GET("/")
        @JsonBody
        public Stream<Person> getPeople(@JsonBody List<Person> persons) {
            return persons.stream();
        }

        @GET("/json")
        @JsonBody(nameFormat = JsonBody.Naming.UNDERSCORE)
        public List<Person> toUpper(@JsonBody Stream<Person> persons) {
            return persons.map(p -> new Person(p.getFirstName().toUpperCase(), p.getLastName().toUpperCase())).collect(Collectors.toList());
        }
    }

    @Test
    public void shouldReturnStream() throws ServletException, MalformedURLException {
        String baseUrl = "http://example.com/test";
        final TestController controller = new TestController();
        final URL contextRoot = new URL(baseUrl);
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        TestController client = ApiClientClassProxy.create(TestController.class, new FakeApiClient(contextRoot, "/api", servlet));

        assertThat(client.getPeople(Arrays.asList(new Person("First", "Woman"), new Person("Second", "Man"))))
                .extracting(Person::getLastName)
                .contains("Woman", "Man");
    }

    @Test
    public void shouldSupportUnderscoreMapping() throws MalformedURLException, ServletException {
        String baseUrl = "http://example.com/test";
        final TestController controller = new TestController();
        final URL contextRoot = new URL(baseUrl);
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        TestController client = ApiClientClassProxy.create(TestController.class, new FakeApiClient(contextRoot, "/api", servlet));

        assertThat(client.toUpper(Stream.of(new Person("First", "Woman"))))
                .extracting(Person::getLastName)
                .contains("WOMAN");

    }

}
