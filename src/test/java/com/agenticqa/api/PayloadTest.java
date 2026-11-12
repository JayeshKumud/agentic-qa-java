package com.agenticqa.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

public class PayloadTest {

    @Test
    public void buildsABodyFromNothingWithTypedValues() {
        Payload body = Payload.empty()
                .set("name", "Ada")
                .set("profile.limits.creditEur", 7500)
                .set("profile.vip", true)
                .set("tags[]", "a")
                .set("tags[]", "b")
                .set("note", null);
        assertEquals(body.toString(), "{\"name\":\"Ada\",\"profile\":{\"limits\":{\"creditEur\":7500},"
                + "\"vip\":true},\"tags\":[\"a\",\"b\"],\"note\":null}");
    }

    @Test
    public void startsFromATestDataFile() {
        Payload body = Payload.file("customers/new_customer.json").remove("status").set("name", "Katherine");
        assertEquals(body.get("name").asText(), "Katherine");
        assertNull(body.get("status"));
        assertEquals(body.get("email").asText(), "katherine.johnson.{unique}@example.test");
    }

    @Test
    public void startsFromARecordOrAMapAndCopies() {
        Customer customer = new Customer(1L, "Ada", "ada@example.test", "active");
        Payload update = Payload.of(customer).remove("id").set("status", "inactive");
        assertEquals(update.toString(), "{\"name\":\"Ada\",\"email\":\"ada@example.test\",\"status\":\"inactive\"}");
        assertEquals(update.as(Customer.class), new Customer(null, "Ada", "ada@example.test", "inactive"));

        Payload original = Payload.of(Map.of("items", List.of(1, 2)));
        Payload copy = original.copy().set("items[]", 3);
        assertEquals(original.get("items").size(), 2);
        assertEquals(copy.get("items").size(), 3);
    }

    @Test
    public void mergesAndRejectsTypos() {
        Payload body = Payload.json("{\"a\":{\"b\":1,\"c\":2}}").merge("{\"a\":{\"c\":null,\"d\":3}}");
        assertEquals(body.toString(), "{\"a\":{\"b\":1,\"d\":3}}");
        expectThrows(IllegalArgumentException.class, () -> body.remove("a.nope"));
        expectThrows(IllegalArgumentException.class, () -> Payload.json("\"text\""));
    }

    @Test
    public void serializesAsItsJsonInsideAnotherObject() throws Exception {
        String json = new ObjectMapper().writeValueAsString(Map.of("wrapped", Payload.empty().set("x", 1)));
        assertEquals(json, "{\"wrapped\":{\"x\":1}}");
    }
}
