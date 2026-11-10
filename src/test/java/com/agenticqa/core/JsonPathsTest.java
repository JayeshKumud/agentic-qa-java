package com.agenticqa.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/** Unit tests of the JSON path engine used to build and change request payloads. */
public class JsonPathsTest {

    private static JsonNode doc() {
        return JsonPaths.parse("""
                {"name":"Ada","profile":{"address":{"city":"London"},"tags":["vip","early"]},
                 "items":[{"sku":"A","qty":1},{"sku":"B","qty":2}],"odd.key":"x"}""");
    }

    @Test
    public void readsNestedFieldsAndIndexes() {
        JsonNode d = doc();
        assertEquals(JsonPaths.get(d, "profile.address.city").asText(), "London");
        assertEquals(JsonPaths.get(d, "$.items[1].sku").asText(), "B");
        assertEquals(JsonPaths.get(d, "items[-1].qty").asInt(), 2);
        assertEquals(JsonPaths.get(d, "[\"odd.key\"]").asText(), "x");
        assertEquals(JsonPaths.get(d, "$"), d);
        assertNull(JsonPaths.get(d, "profile.missing.city"));
        assertNull(JsonPaths.get(d, "items[5]"));
    }

    @Test
    public void setsExistingAndCreatesMissingContainers() {
        JsonNode d = doc();
        JsonPaths.set(d, "profile.address.city", JsonPaths.value("Paris"));
        JsonPaths.set(d, "billing.address.lines[0]", JsonPaths.value("1 Main St"));
        JsonPaths.set(d, "items[1].qty", JsonPaths.value("json:5"));
        JsonPaths.set(d, "items[2].sku", JsonPaths.value("C"));
        assertEquals(JsonPaths.get(d, "profile.address.city").asText(), "Paris");
        assertEquals(JsonPaths.get(d, "billing.address.lines[0]").asText(), "1 Main St");
        assertTrue(JsonPaths.get(d, "items[1].qty").isInt());
        assertEquals(JsonPaths.get(d, "items").size(), 3);
    }

    @Test
    public void appendsWithEmptyBrackets() {
        JsonNode d = doc();
        JsonPaths.set(d, "profile.tags[]", JsonPaths.value("beta"));
        JsonPaths.set(d, "items[].sku", JsonPaths.value("D"));
        assertEquals(JsonPaths.get(d, "profile.tags[2]").asText(), "beta");
        assertEquals(JsonPaths.get(d, "items[2].sku").asText(), "D");
    }

    @Test
    public void typedValuesNeedThePrefix() {
        assertTrue(JsonPaths.value("42").isTextual());
        assertTrue(JsonPaths.value("json:42").isInt());
        assertTrue(JsonPaths.value("json:true").isBoolean());
        assertTrue(JsonPaths.value("json:null").isNull());
        assertTrue(JsonPaths.value("json:{\"a\":[1,2]}").isObject());
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.value("json:{broken"));
    }

    @Test
    public void removesFieldsAndElements() {
        JsonNode d = doc();
        JsonPaths.remove(d, "profile.address");
        JsonPaths.remove(d, "items[0]");
        assertNull(JsonPaths.get(d, "profile.address"));
        assertEquals(JsonPaths.get(d, "items[0].sku").asText(), "B");
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.remove(d, "profile.nope"));
    }

    @Test
    public void mergeFollowsMergePatchRules() {
        JsonNode d = doc();
        JsonPaths.merge(d, JsonPaths.parse("""
                {"profile":{"address":{"zip":"N1"},"tags":["only"]},"name":null,"status":"inactive"}"""));
        assertEquals(JsonPaths.get(d, "profile.address.city").asText(), "London");
        assertEquals(JsonPaths.get(d, "profile.address.zip").asText(), "N1");
        assertEquals(JsonPaths.get(d, "profile.tags").size(), 1);
        assertNull(JsonPaths.get(d, "name"));
        assertEquals(JsonPaths.get(d, "status").asText(), "inactive");
    }

    @Test
    public void rejectsPathsThatDoNotFitTheDocument() {
        JsonNode d = doc();
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.set(d, "items[7].sku", JsonPaths.value("Z")));
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.set(d, "name.first", JsonPaths.value("A")));
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.get(d, "items[x]"));
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.get(d, "a..b"));
    }

    @Test
    public void matchesTextOrJson() {
        JsonNode d = doc();
        assertTrue(JsonPaths.matches(JsonPaths.get(d, "items[0].qty"), "1"));
        assertTrue(JsonPaths.matches(JsonPaths.get(d, "items[0].qty"), "json:1"));
        assertFalse(JsonPaths.matches(JsonPaths.get(d, "items[0].qty"), "json:\"1\""));
        assertTrue(JsonPaths.matches(JsonPaths.get(d, "profile.tags"), "json:[\"vip\",\"early\"]"));
        assertFalse(JsonPaths.matches(null, "anything"));
    }
}
