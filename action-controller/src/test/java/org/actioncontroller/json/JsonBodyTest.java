package org.actioncontroller.json;

import org.actioncontroller.GET;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.POST;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonBodyTest {

    protected TestController client;

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
        @POST("/")
        @JsonBody
        public Stream<Person> getPeople(@JsonBody List<Person> persons) {
            return persons.stream();
        }

        @POST("/json")
        @JsonBody(nameFormat = JsonBody.Naming.UNDERSCORE)
        public List<Person> toUpper(@JsonBody Stream<Person> persons) {
            return persons.map(p -> new Person(p.getFirstName().toUpperCase(), p.getLastName().toUpperCase())).collect(Collectors.toList());
        }

        @GET("/error")
        @JsonBody
        public JsonBodyTest.Person throwError() {
            throw new HttpRequestException("Missing value foo");
        }
    }

    @Before
    public void setUp() throws Exception {
        final ApiServlet servlet = new ApiServlet(new TestController());
        servlet.init(null);
        FakeApiClient client = new FakeApiClient(new URL("http://example.com/test"), "/api", servlet);
        this.client = ApiClientClassProxy.create(TestController.class, client);
    }

    @Test
    public void shouldReturnStream() {
        assertThat(client.getPeople(Arrays.asList(new Person("First", "Woman"), new Person("Second", "Man"))))
                .extracting(Person::getLastName)
                .contains("Woman", "Man");
    }

    @Test
    public void shouldSupportUnderscoreMapping() {
        assertThat(client.toUpper(Stream.of(new Person("First", "Woman"))))
                .extracting(Person::getLastName)
                .contains("WOMAN");
    }

    @Test
    public void shouldConvertErrorToJson() {
        assertThatThrownBy(() -> client.throwError())
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getResponseBody()).contains("\"message\":\"Missing value foo\""));
    }
}
