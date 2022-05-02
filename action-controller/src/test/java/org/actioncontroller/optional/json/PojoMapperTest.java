package org.actioncontroller.optional.json;

import org.actioncontroller.optional.json.JsonTestModel.ExampleClass;
import org.actioncontroller.optional.json.JsonTestModel.ExampleInterface;
import org.actioncontroller.optional.json.JsonTestModel.FirstImplementation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class PojoMapperTest {

    public Stream<String> exampleField;

    @Test
    public void shouldMapNull() {
        assertThat(new PojoMapper().map(null, FirstImplementation.class)).isNull();
    }

    @Test
    public void shouldMapToSimpleType() {
        JsonObject json = Json.createObjectBuilder()
                .add("type", "first")
                .add("value", "some value")
                .build();

        FirstImplementation pojo = new PojoMapper().map(json, FirstImplementation.class);

        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapOneOfType() {
        JsonObject json = Json.createObjectBuilder()
                .add("type", "first")
                .add("value", "some value")
                .build();

        ExampleInterface pojo = new PojoMapper()
                .addObjectFactoryMapper(ExampleInterface.class, "type", ExampleInterface::fromType)
                .map(json, ExampleInterface.class);
        assertThat(pojo)
                .asInstanceOf(InstanceOfAssertFactories.type(FirstImplementation.class))
                .satisfies(o -> assertThat(o.getValue()).isEqualTo("some value"));
        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapListOfStrings() {
        JsonObject json = Json.createObjectBuilder()
                .add("tags", Json.createArrayBuilder(List.of("a", "b", "c")))
                .build();

        ExampleClass pojo = new PojoMapper().map(json, ExampleClass.class);
        assertThat(pojo.getTags()).containsExactly("a", "b", "c");
        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapStreamOfStrings() {
        JsonArray json = new JsonGenerator().toJson(Stream.of("a", "b", "c")).asJsonArray();
        assertThat(json.stream().map(v -> ((JsonString) v).getString())).containsExactly("a", "b", "c");
    }

    @Test
    public void shouldMapArray() {
        JsonArray json = new JsonGenerator().toJson(new String[]{"a", "b", "c"}).asJsonArray();
        assertThat(json.stream().map(v -> ((JsonString) v).getString())).containsExactly("a", "b", "c");
        assertThat(new PojoMapper().map(json, String[].class))
                .isEqualTo(new String[]{"a", "b", "c"});
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldMapStream() throws NoSuchFieldException {
        assertThat((Stream<String>) new PojoMapper().map(
                Json.createArrayBuilder().add("a").add("b").add("c").build(),
                PojoMapperTest.class.getField("exampleField").getGenericType())
        )
                .containsExactly("a", "b", "c");
    }

    @Test
    public void shouldMapMapOfObjects() {
        UUID id = UUID.randomUUID();
        JsonObject json = Json.createObjectBuilder()
                .add("children", Json.createObjectBuilder()
                        .add(id.toString(), Json.createObjectBuilder().add("type", "first").add("value", "Data Value"))
                ).build();

        ExampleClass pojo = new PojoMapper()
                .addObjectFactoryMapper(ExampleInterface.class, "type", ExampleInterface::fromType)
                .map(json, ExampleClass.class);
        assertThat((pojo.getChildren().get(id)))
                .asInstanceOf(InstanceOfAssertFactories.type(FirstImplementation.class))
                .extracting(FirstImplementation::getValue)
                .isEqualTo("Data Value");
        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapValueTypes() {
        JsonObject json = Json.createObjectBuilder()
                .add("name", "Some name")
                .add("number", 123)
                .add("valid", true)
                .add("exampleEnum", "ONE")
                .build();
        ExampleClass pojo = new PojoMapper().map(json, ExampleClass.class);
        assertThat(pojo.getName()).isEqualTo("Some name");
        assertThat(pojo.getNumber()).isEqualTo(123);
        assertThat(pojo.getValid()).isEqualTo(true);
        assertThat(pojo.getExampleEnum()).get().isEqualTo(JsonTestModel.ExampleEnum.ONE);

        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapWithUnderscores() {
        String randomValue = UUID.randomUUID().toString();
        JsonObject json = Json.createObjectBuilder()
                .add("example_enum", "ONE")
                .add("children", Json.createObjectBuilder()
                        .add(UUID.randomUUID().toString(), Json.createObjectBuilder().add("type", "first").add("other_value", randomValue))
                ).build();

        ExampleClass pojo = new PojoMapper()
                .rejectUnknownFields()
                .addObjectFactoryMapper(ExampleInterface.class, "type", ExampleInterface::fromType)
                .map(json, ExampleClass.class);

        assertThat(new JsonGenerator().withNameTransformer(JsonGenerator.UNDERSCORE).toJson(pojo)).isEqualTo(json);
    }

    @Test
    public void shouldMapGenericClasses() {
        JsonObject objectOne = Json.createObjectBuilder()
                .add("type", "first")
                .add("value", "some value")
                .build();
        JsonObject objectTwo = Json.createObjectBuilder()
                .add("type", "second")
                .add("value", "other value")
                .build();
        JsonObject combined = Json.createObjectBuilder()
                .add("objectOne", objectOne)
                .add("objectTwo", objectTwo)
                .build();

        JsonTestModel.SpecifiedSubClass pojo = new PojoMapper().map(combined, JsonTestModel.SpecifiedSubClass.class);

        assertThat(new JsonGenerator().toJson(pojo)).isEqualTo(combined);
    }

    @Test
    public void shouldWarnOnExtraProperties() {
        JsonObject objectOne = Json.createObjectBuilder()
                .add("type", "first")
                .add("value", "some value")
                .add("noSuchField", "some value")
                .build();
        FirstImplementation pojo = new PojoMapper().map(objectOne, FirstImplementation.class);
    }

}
