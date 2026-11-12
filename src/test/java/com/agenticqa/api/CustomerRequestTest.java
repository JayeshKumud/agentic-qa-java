package com.agenticqa.api;

import com.agenticqa.api.model.CustomerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class CustomerRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void serializesWithoutNullFields() throws Exception {
        CustomerRequest request = new CustomerRequest().setName("Ada").setEmail("ada@example.test");
        assertEquals(MAPPER.writeValueAsString(request), "{\"name\":\"Ada\",\"email\":\"ada@example.test\"}");
    }

    @Test
    public void readsBackAndIgnoresUnknownFields() throws Exception {
        CustomerRequest read = MAPPER.readValue(
                "{\"id\":7,\"name\":\"Ada\",\"email\":\"a@x\",\"status\":\"active\"}", CustomerRequest.class);
        assertEquals(read, new CustomerRequest("Ada", "a@x", "active"));
    }

    @Test
    public void canBeTurnedIntoAPayloadForNegativeTests() {
        Payload payload = Payload.of(new CustomerRequest("Ada", "a@x", null)).set("status", 42);
        assertEquals(payload.toString(), "{\"name\":\"Ada\",\"email\":\"a@x\",\"status\":42}");
    }
}
