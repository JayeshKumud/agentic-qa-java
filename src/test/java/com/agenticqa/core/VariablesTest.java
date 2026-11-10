package com.agenticqa.core;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Unit tests of the scenario variables used to chain requests. */
public class VariablesTest {

    @Test
    public void replacesVariablesAndTheUniqueToken() {
        Variables vars = new Variables();
        vars.put("customerId", "42");
        vars.put("region", "eu-west");
        String resolved = vars.resolve("/customers/${customerId}?region=${region}&tag={unique}&again={unique}");
        assertTrue(resolved.startsWith("/customers/42?region=eu-west&tag="), resolved);
        assertEquals(resolved, "/customers/42?region=eu-west&tag=" + vars.token() + "&again=" + vars.token());
    }

    @Test
    public void valuesWithDollarSignsAreInsertedLiterally() {
        Variables vars = new Variables();
        vars.put("price", "$5 \\ off");
        assertEquals(vars.resolve("costs ${price}"), "costs $5 \\ off");
    }

    @Test
    public void unknownVariableFailsWithTheKnownNames() {
        Variables vars = new Variables();
        vars.put("orderId", "7");
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> vars.resolve("/x/${customerId}"));
        assertTrue(e.getMessage().contains("orderId"), e.getMessage());
    }

    @Test
    public void rejectsOddNames() {
        assertThrows(IllegalArgumentException.class, () -> new Variables().put("has space", "x"));
    }
}
