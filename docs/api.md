# API classes

API calls are written the same way as in the existing framework: one **API class** per service, created by
**`ApiFactory`**, calling the service with **REST Assured**. Request and response bodies are **model classes**.
API steps and GUI steps share data through the scenario's `TestSession`, so one scenario can create data
through the API and check it in the browser, or the other way round.

## The pieces

| Piece | Where | What it does |
|---|---|---|
| `Api` | `src/main/java/com/agenticqa/api/Api.java` | Marker interface every API class implements |
| `ApiFactory` | `src/main/java/com/agenticqa/api/ApiFactory.java` | `getApi(XxxApi.class)` creates each API class once and returns the same instance on every call |
| `Config.getApiUrl(name)` | `src/main/java/com/agenticqa/config/Config.java` | Base URL of a service in the active environment: `services.<name>.baseUrl` |
| API classes | `src/test/java/com/agenticqa/apis/` | `CustomerApi`, `WebserviceAdapterValidationApi`: one method per call, REST Assured, `Assert.fail` on an unexpected status |
| Models | `src/test/java/com/agenticqa/apis/model/` | `Customer`: the request and response body, serialized by Jackson |
| Steps | `src/test/java/com/agenticqa/steps/CustomerApiSteps.java` | Call the API classes and store the result in the `TestSession` |

## An API class

```java
public class CustomerApi implements Api {
    private final String baseUrl;
    private final String customersPath;

    public CustomerApi() {                                    // no-argument constructor, called by ApiFactory
        this.baseUrl = Config.getApiUrl("customers");         // services.customers.baseUrl of the active env
        this.customersPath = "/customers";
    }

    public Response postCustomer(Object customerToPost) {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .contentType(ContentType.JSON)
                .body(customerToPost)                         // a model object, serialized to JSON
                .post(baseUrl + customersPath);
        if (response.getStatusCode() != 201) {
            Assert.fail("Error posting Customer. Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }
}
```

`CustomerApi` also has `getCustomer(id)`, `getCustomers()`, `putCustomer(id, body)` and `deleteCustomer(id)`.
`WebserviceAdapterValidationApi.postDeclaration(declaration)` is the same pattern for the `appWebserviceAdapter`
service.

- **One instance for the whole run.** `ApiFactory.getApi` creates each API class once and returns the same
  instance to every scenario and thread. An API class therefore keeps no per-scenario state, only final
  settings such as its base URL and paths. Per-scenario data goes into the `TestSession`.
- **Base URLs per environment.** `Config.getApiUrl(name)` reads `services.<name>.baseUrl` from the merged config
  (common.yaml, the environment file, `-D` overrides), so `-Denv=uat` or
  `-Dservices.customers.baseUrl=http://...` switches the API classes too. A service that is not configured
  fails with a message that names the environment file.
- **Test scope.** REST Assured and TestNG are test dependencies, so API classes and models live under
  `src/test/java` and can use `Assert.fail`.
- **No browser.** API classes call their services directly, so API-only scenarios never start a browser.

## Models

```java
Customer created = ApiFactory.getApi(CustomerApi.class)
        .postCustomer(new Customer(name, email))   // request body: null fields (id, status) are left out
        .as(Customer.class);                       // response body: unknown fields are ignored
```

`Customer` is a plain class with getters and setters and a no-argument constructor for Jackson. A new service
gets its own model classes next to it in `apis/model/`.

## Sharing data between API and GUI steps

Every step class gets the scenario's `TestSession` (from PicoContainer, or `TestSession.current()`), and they all
store and read through typed keys (`steps/Keys.java`):

| Key | Type | Written by | Read by |
|---|---|---|---|
| `Keys.CUSTOMER` | `Customer` | API steps (created or updated customer), GUI step "I add a customer ... through the form" (id read from the page) | API checks by id, GUI search and row comparison |
| `Keys.RESPONSE` | REST Assured `Response` | every API step | `Then the API responds with status {int}` |
| `Keys.NAME_TAG` | `String` | `Given {int} customers share a unique name tag` | GUI search for the tag |

```gherkin
When I add a customer named "Katherine Johnson" through the form     # GUI: stores the customer
Then the customer can be found by id with status "active"           # API: reads its id
When I change the customer status to "inactive"                     # API: stores the updated customer
And I search the customers page for that customer                   # GUI: searches for its name
Then the results show only that customer with status "inactive"     # GUI: compares the row with it
```

See `features/e2e/customer_journey.feature`.

## Cleanup

Whatever a scenario creates is deleted after it, pass or fail. Steps register a delete with `ServiceCleanup`,
which PicoContainer shares between the step classes of a scenario:

```java
cleanup.add("customer " + id, () -> ApiFactory.getApi(CustomerApi.class).deleteCustomer(id));
cleanup.cancel("customer " + id);   // the scenario deleted it itself
```

The deletes run newest first in an `@After(order = 2)` hook, after `TestInitializeHooks` has captured the failure evidence and
closed the browser, and before `teardownData` deletes the zone. They
are best effort: a failing delete (including an `Assert.fail` inside an API class) is ignored, so it cannot
hide the real result of the scenario.

## Adding an API

1. Add the service's base URL to every environment file under `services:`.
2. Add an API class in `apis/` like `CustomerApi`: `implements Api`, a no-argument constructor with
   `Config.getApiUrl("<service>")`, one method per call that returns the `Response`.
3. Add model classes in `apis/model/` for its bodies.
4. Call it from steps with `ApiFactory.getApi(XxxApi.class)`, store results with `session.put(key, value)`, and
   register what it creates with `cleanup.add(...)`.
