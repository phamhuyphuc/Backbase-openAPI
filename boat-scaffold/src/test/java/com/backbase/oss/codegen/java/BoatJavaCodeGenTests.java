package com.backbase.oss.codegen.java;

import static com.backbase.oss.codegen.java.BoatJavaCodeGen.*;

import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;

class BoatJavaCodeGenTests {

    @Test
    void clientOptsUnicity() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        gen.cliOptions()
            .stream()
            .collect(groupingBy(CliOption::getOpt))
            .forEach((k, v) -> assertEquals(1, v.size(), k + " is described multiple times"));
    }

    @Test
    void processOptsWithRestTemplateDefaults() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();

        gen.setLibrary("resttemplate");
        gen.processOpts();

        assertThat(gen.useWithModifiers, is(false));
        assertThat(gen.useSetForUniqueItems, is(false));
        assertThat(gen.useClassLevelBeanValidation, is(false));

        assertThat(gen.useJacksonConversion, is(false));
        assertThat(gen.useDefaultApiClient, is(true));
        assertThat(gen.restTemplateBeanName, is(nullValue()));
    }

    @Test
    void processOptsWithRestTemplate() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final Map<String, Object> options = gen.additionalProperties();

        gen.setLibrary("resttemplate");
        options.put(USE_WITH_MODIFIERS, "true");
        options.put(USE_SET_FOR_UNIQUE_ITEMS, "true");
        options.put(USE_CLASS_LEVEL_BEAN_VALIDATION, "true");

        options.put(USE_JACKSON_CONVERSION, "true");
        options.put(USE_DEFAULT_API_CLIENT, "false");
        options.put(REST_TEMPLATE_BEAN_NAME, "the-coolest-rest-template-in-this-universe");

        gen.processOpts();

        assertThat(gen.useWithModifiers, is(true));
        assertThat(gen.useSetForUniqueItems, is(true));
        assertThat(gen.useClassLevelBeanValidation, is(true));

        assertThat(gen.useJacksonConversion, is(true));
        assertThat(gen.useDefaultApiClient, is(false));
        assertThat(gen.restTemplateBeanName, is("the-coolest-rest-template-in-this-universe"));
    }

    @Test
    void processOptsWithoutRestTemplate() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final Map<String, Object> options = gen.additionalProperties();

        options.put(USE_WITH_MODIFIERS, "true");
        options.put(USE_SET_FOR_UNIQUE_ITEMS, "true");
        options.put(USE_CLASS_LEVEL_BEAN_VALIDATION, "true");

        options.put(USE_JACKSON_CONVERSION, "true");
        options.put(USE_DEFAULT_API_CLIENT, "false");
        options.put(REST_TEMPLATE_BEAN_NAME, "the-coolest-rest-template-in-this-universe");

        gen.processOpts();

        assertThat(gen.useWithModifiers, is(true));
        assertThat(gen.useSetForUniqueItems, is(true));
        assertThat(gen.useClassLevelBeanValidation, is(false));

        assertThat(gen.useJacksonConversion, is(false));
        assertThat(gen.useDefaultApiClient, is(true));
        assertThat(gen.restTemplateBeanName, is(nullValue()));
    }

    @Test
    void processOptsCreateApiComponent() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final Map<String, Object> options = gen.additionalProperties();

        options.put(CREATE_API_COMPONENT, "false");

        gen.setLibrary("resttemplate");
        gen.processOpts();

        assertThat(gen.createApiComponent, is(false));
    }

    @Test
    void processOptsUseProtectedFields() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final Map<String, Object> options = gen.additionalProperties();

        options.put(USE_PROTECTED_FIELDS, "true");

        gen.processOpts();

        assertThat(gen.additionalProperties(), hasEntry("modelFieldsVisibility", "protected"));
    }

    @Test
    @Disabled("Since useSetForUniqueItems is not supported and it is enabled by default")
    void uniquePropertyToSet() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final CodegenProperty prop = new CodegenProperty();

        gen.useSetForUniqueItems = true;
        prop.isContainer = true;
        prop.setUniqueItems(true);
        prop.items = new CodegenProperty();
        prop.items.dataType = "String";
        prop.baseType = "java.util.List";
        prop.dataType = "java.util.List<String>";

        gen.postProcessModelProperty(new CodegenModel(), prop);

        assertThat(prop.containerType, is("set"));
        assertThat(prop.baseType, is("java.util.Set"));
        assertThat(prop.dataType, is("java.util.Set<String>"));
    }

    @Test
    @Disabled("Since useSetForUniqueItems is not supported and it is enabled by default")
    void uniqueParameterToSet() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        final CodegenParameter param = new CodegenParameter();

        gen.useSetForUniqueItems = true;
        param.isContainer = true;
        param.setUniqueItems(true);
        param.items = new CodegenProperty();
        param.items.dataType = "String";
        param.baseType = "java.util.List<String>";
        param.dataType = "java.util.List<String>";

        gen.postProcessParameter(param);

        assertThat(param.baseType, is("java.util.Set"));
        assertThat(param.dataType, is("java.util.Set<String>"));
    }

    @Test
    void setTypeMappingForList() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        gen.setFullJavaUtil(false);
        gen.instantiationTypes().put("set", "ArrayList");
        gen.typeMapping().put("set", "List");

        final CodegenProperty prop = new CodegenProperty();
        prop.isContainer = true;
        prop.containerType = "set";
        prop.setUniqueItems(true);
        prop.items = new CodegenProperty();
        prop.items.dataType = "String";
        prop.baseType = "java.util.Set";
        prop.dataType = "java.util.HashSet<String>";

        final var model = new CodegenModel();

        gen.postProcessModelProperty(model, prop);

        assertTrue(model.getImports().contains("ArrayList"));
    }

    @Test
    void arrayTypeMappingForList() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        gen.setFullJavaUtil(false);
        gen.instantiationTypes().put("array", "ArrayList");
        gen.typeMapping().put("array", "List");

        final CodegenProperty prop = new CodegenProperty();
        prop.isContainer = true;
        prop.containerType = "array";
        prop.setUniqueItems(true);
        prop.items = new CodegenProperty();
        prop.items.dataType = "String";
        prop.baseType = "java.util.Set";
        prop.dataType = "java.util.HashSet<String>";

        final var model = new CodegenModel();

        gen.postProcessModelProperty(model, prop);

        assertTrue(model.getImports().contains("ArrayList"));
    }

    @Test
    void mapTypeMappingForMap() {
        final BoatJavaCodeGen gen = new BoatJavaCodeGen();
        gen.setFullJavaUtil(false);
        gen.instantiationTypes().put("map", "TreeMap");

        final CodegenProperty prop = new CodegenProperty();
        prop.isContainer = true;
        prop.containerType = "map";
        prop.setUniqueItems(true);
        prop.items = new CodegenProperty();
        prop.items.dataType = "String";
        prop.baseType = "java.util.Set";
        prop.dataType = "java.util.HashSet<String>";

        final var model = new CodegenModel();

        gen.postProcessModelProperty(model, prop);

        assertTrue(model.getImports().contains("TreeMap"));
    }
}
