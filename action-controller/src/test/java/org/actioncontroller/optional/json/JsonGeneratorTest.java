package org.actioncontroller.optional.json;

import org.junit.Test;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonGeneratorTest {


    @Test
    public void shouldCreateAddPatch() {
        JsonGenerator generator = new JsonGenerator();
        JsonTestModel.ExampleClass pojo = new JsonTestModel.ExampleClass();
        pojo.setChildren(new HashMap<>());
        JsonObject json = generator.objectToJson(pojo);

        UUID childId = UUID.randomUUID();
        JsonTestModel.FirstImplementation child = new JsonTestModel.FirstImplementation().type("first");
        child.setValue("value");
        child.setOtherValue("other value");

        json = generator.addPatch("/children/" + childId, child).apply(json);

        JsonTestModel.FirstImplementation updatedChild = new JsonTestModel.FirstImplementation().type("first");
        updatedChild.setOtherValue("updated other value");
        json = generator.updatePatch("/children/" + childId, updatedChild).apply(json);

        PojoMapper pojoMapper = new PojoMapper()
                .addObjectMapper(
                        JsonTestModel.ExampleInterface.class,
                        (o, mapper) -> mapper.writeFields(
                                (JsonObject) o,
                                JsonTestModel.ExampleInterface.fromType(((JsonObject) o).getString("type"))
                        )
                );
        JsonTestModel.ExampleClass o = pojoMapper.map(json, JsonTestModel.ExampleClass.class);
        JsonTestModel.FirstImplementation actualChild =
                (JsonTestModel.FirstImplementation) o.getChildren().get(childId);
        assertThat(actualChild.getValue()).isEqualTo(child.getValue());
        assertThat(actualChild.getOtherValue()).isEqualTo(updatedChild.getOtherValue());
    }
}
