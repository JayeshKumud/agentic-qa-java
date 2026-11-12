package com.agenticqa.api;

import com.agenticqa.api.Endpoints.Endpoint;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class EndpointsTest {

    @Test
    public void readsAnEndpointFromTheCatalog() {
        Endpoint create = Endpoints.get("customers.create");
        assertEquals(create.service(), "customers");
        assertEquals(create.method(), "POST");
        assertEquals(create.path(), "/customers");
        assertEquals(create.body(), "customers/new_customer.json");
        assertNull(Endpoints.get("customers.get").body());
    }

    @Test
    public void fillsAndEncodesPathParameters() {
        Endpoint get = Endpoints.get("customers.get");
        assertEquals(List.copyOf(get.parameters()), List.of("id"));
        assertEquals(get.path(Map.of("id", 42), null), "/customers/42");
        assertEquals(get.path(Map.of("id", "a b/c"), null), "/customers/a%20b%2Fc");
        assertEquals(get.path(Map.of(), name -> name.equals("id") ? "7" : null), "/customers/7");
    }

    @Test
    public void explicitParametersWinOverTheFallback() {
        assertEquals(Endpoints.get("customers.get").path(Map.of("id", 1), name -> "2"), "/customers/1");
    }

    @Test
    public void reportsAMissingParameter() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> Endpoints.get("customers.get").path(Map.of(), null));
        assertTrue(e.getMessage().contains("needs path parameter 'id'"), e.getMessage());
    }

    @Test
    public void listsTheEndpointsOfAFileWhenTheKeyIsUnknown() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> Endpoints.get("customers.nope"));
        assertTrue(e.getMessage().contains("[create, delete, get, list, update]"), e.getMessage());
    }

    @Test
    public void reportsAMissingFileAndABadName() {
        expectThrows(IllegalArgumentException.class, () -> Endpoints.get("nosuchservice.get"));
        expectThrows(IllegalArgumentException.class, () -> Endpoints.get("customers"));
    }
}
