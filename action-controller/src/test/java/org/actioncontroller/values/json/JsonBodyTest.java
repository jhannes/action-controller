package org.actioncontroller.values.json;

import org.actioncontroller.values.ContentBody;
import org.actioncontroller.actions.GET;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.actions.POST;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.jsonbuddy.JsonArray;
import org.jsonbuddy.pojo.JsonGenerator;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonBodyTest {

    protected TestController client;
    private ApiClient httpClient;

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

        @POST("/getName")
        @ContentBody
        public String getName(@JsonBody Person person) {
            return person.getFirstName() + " " + person.getLastName();
        }

        @POST("/optionalName")
        @ContentBody
        public String optionalName(@JsonBody Optional<Person> person) {
            return person.map(this::getName).orElse("<missing>");
        }
    }

    @Before
    public void setUp() throws Exception {
        this.httpClient = createHttpClient(new TestController());
        this.client = ApiClientClassProxy.create(TestController.class, this.httpClient);
    }

    protected ApiClient createHttpClient(Object controller) throws Exception {
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        return new FakeApiClient(new URL("http://example.com/test"), "/api", servlet);
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
    public void shouldMapToUnderscore() throws IOException {
        ApiClientExchange exchange = httpClient.createExchange();
        exchange.setTarget("POST", "/json");
        List<Person> people = Arrays.asList(new Person("First", "Woman"), new Person("Second", "Man"));
        exchange.write(
                "application/json",
                writer -> JsonGenerator.generate(people).toJson(writer)
        );
        exchange.executeRequest();
        exchange.checkForError();
        JsonArray response = JsonArray.read(exchange.getResponseBodyReader());
        assertThat(response.requiredObject(0).requiredString("first_name")).isEqualTo("FIRST");
        assertThat(response.requiredObject(1).requiredString("first_name")).isEqualTo("SECOND");
    }

    @Test
    public void shouldConvertErrorToJson() {
        assertThatThrownBy(() -> client.throwError())
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getResponseBody()).contains("\"message\":\"Missing value foo\""));
    }

    @Test
    public void shouldAcceptMissingOptional() {
        assertThat(client.optionalName(Optional.empty())).isEqualTo("<missing>");
    }

    @Test
    public void shouldAcceptOptional() {
        assertThat(client.optionalName(Optional.of(new Person("Jane", "Doe")))).isEqualTo("Jane Doe");
    }

    @Test
    public void shouldReturn400OnMissingJson() throws IOException {
        ApiClientExchange exchange = httpClient.createExchange();
        exchange.setTarget("POST", "/getName");
        exchange.write("application/json", writer -> writer.write("null"));
        exchange.executeRequest();
        assertThatThrownBy(exchange::checkForError)
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getResponseBody()).contains("Missing required request body"));
    }
}
