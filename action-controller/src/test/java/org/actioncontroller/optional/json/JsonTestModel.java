package org.actioncontroller.optional.json;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JsonTestModel {

    public enum ExampleEnum {
        ONE, TWO, THREE
    }

    public static class ExampleClass {
        private String name;
        private Integer number;
        private Boolean valid;
        private Optional<ExampleEnum> exampleEnum;
        private Map<UUID, ExampleInterface> children;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        public Boolean getValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public Optional<ExampleEnum> getExampleEnum() {
            return exampleEnum;
        }

        public void setExampleEnum(Optional<ExampleEnum> exampleEnum) {
            this.exampleEnum = exampleEnum;
        }

        public Map<UUID, ExampleInterface> getChildren() {
            return children;
        }

        public void setChildren(Map<UUID, ExampleInterface> children) {
            this.children = children;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

    }

    public interface ExampleInterface {
        String getType();

        static ExampleInterface fromType(String type) {
            if (type.equals("first")) {
                return new FirstImplementation().type(type);
            } else if (type.equals("second")) {
                return new SecondImplementation().type(type);
            } else {
                throw new IllegalArgumentException("Unknown type value " + type);
            }
        }
    }

    public static class FirstImplementation implements ExampleInterface {
        private String type;

        private String value;
        private String otherValue;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getOtherValue() {
            return otherValue;
        }

        public void setOtherValue(String otherValue) {
            this.otherValue = otherValue;
        }

        public FirstImplementation type(String type) {
            this.type = type;
            return this;
        }

        @Override
        public String getType() {
            return type;
        }
    }

    public static class SecondImplementation implements ExampleInterface {
        private String type;
        public SecondImplementation type(String type) {
            this.type = type;
            return this;
        }

        @Override
        public String getType() {
            return type;
        }
    }


    public static class GenericClass<T, U> {
        private T objectOne;
        private U objectTwo;

        public T getObjectOne() {
            return objectOne;
        }

        public void setObjectOne(T objectOne) {
            this.objectOne = objectOne;
        }

        public U getObjectTwo() {
            return objectTwo;
        }

        public void setObjectTwo(U objectTwo) {
            this.objectTwo = objectTwo;
        }
    }

    public static class SpecifiedClass extends GenericClass<FirstImplementation, FirstImplementation> {

    }

    public static class SpecifiedSubClass extends SpecifiedClass {

    }


}
