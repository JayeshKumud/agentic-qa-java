# API requests: many requests, large payloads, several services, and the GUI

One scenario can build several requests, change them over any number of steps, send them to different services,
chain values from one response into the next request, and switch between API and browser steps. All of this
happens without writing Java for each endpoint.

See [`multi_request_journey.feature`](../src/test/resources/features/e2e/multi_request_journey.feature) for all of
it in one scenario, and [`generic_http_requests.feature`](../src/test/resources/features/api/generic_http_requests.feature)
for smaller examples.

## Building blocks

| Class | Role |
|---|---|
| [`ApiState`](../src/main/java/com/agenticqa/api/ApiState.java) | The scenario's **named requests** (body as JSON text plus headers) and **named responses**. One per scenario, injected by PicoContainer. |
| [`Variables`](../src/main/java/com/agenticqa/core/Variables.java) | Scenario variables: `${name}` and the `{unique}` token. |
| [`JsonPaths`](../src/main/java/com/agenticqa/core/JsonPaths.java) | Reads and changes values deep inside JSON by path. Handles merge patches and typed values. Unit tested. |
| [`ServiceClient`](../src/main/java/com/agenticqa/api/ServiceClient.java) | Sends any method to any configured service. |
| [`Endpoints`](../src/main/java/com/agenticqa/api/Endpoints.java) | The endpoint catalog: endpoints declared once in `config/common.yaml`, called by name (`customers.get`). |
| [`ServiceAuth`](../src/main/java/com/agenticqa/api/ServiceAuth.java) | Adds a service's Authorization header from its `auth` settings (OAuth2 client credentials, basic, bearer). |
| [`CustomerRequest`](../src/main/java/com/agenticqa/api/model/CustomerRequest.java) | An example request POJO; any POJO can be a request body. |
| [`EndpointClient`](../src/main/java/com/agenticqa/api/EndpointClient.java) | Calls any catalog endpoint from Java: `call("customers.get", Map.of("id", 42))`. |
| [`Payload`](../src/main/java/com/agenticqa/api/Payload.java) | Any JSON request body in Java, built from a file, map, record or response and edited by path. |
| [`HttpSteps`](../src/test/java/com/agenticqa/steps/HttpSteps.java) | The generic Gherkin steps listed below. |
| [`ServiceCleanup`](../src/test/java/com/agenticqa/steps/ServiceCleanup.java) | DELETEs registered resources after the scenario, newest first, whether it passed or failed. |
| [`CustomerRequestSteps`](../src/test/java/com/agenticqa/steps/CustomerRequestSteps.java) | The customer steps, plus the **bridge** between the generic steps and the customer API and GUI steps. |

## Services (microservices)

Every service is configured by name in `config/env/<env>.yaml`, with what differs per environment: its URL
and its credentials. Headers every service gets (`serviceDefaults.headers`) are in `config/common.yaml`.

```yaml
services:
  customers:
    baseUrl: http://127.0.0.1:8000
  orders:                          # a second microservice is just another entry
    baseUrl: ${UAT_ORDERS_URL:-https://uat-api.orders.example.com}
    timeoutMs: 30000               # optional, defaults to api.timeoutMs
    headers:
      X-Tenant: qa                 # sent with every request to this service, on top of serviceDefaults
    auth:                          # optional: none (default), oauth2, basic or bearer
      type: oauth2
      tokenUrl: ${UAT_AUTH_URL}
      clientId: ${UAT_ORDERS_CLIENT_ID}
      clientSecret: ${UAT_ORDERS_CLIENT_SECRET}
```

With `oauth2`, a token is fetched once per run (client credentials grant), shared by all threads and renewed
before it expires; every request gets `Authorization: Bearer <token>`. Secrets are masked in logs and reports.
See [running-tests.md](running-tests.md#urls-client-ids-and-secrets-environment-files).

Steps address a service by its name (`on the "orders" service`). You can override a URL per run with
`-Dservices.orders.baseUrl=https://orders.staging.example`. An unknown name fails with the list of configured
services. The endpoint catalog (below) names its services the same way.

All services share the scenario's browser context, so cookies set by a login on one service or on the page are
also sent to the others.

## Steps

### Build a request

| Step | Notes |
|---|---|
| `Given a new "order" request from "orders/large_order.json"` | File under `src/test/resources/testdata`. Use for large payloads. |
| `Given a new "order" request:` + table of `path \| value` | Builds the body from nothing; missing objects and arrays are created. |
| `Given a new "order" request with body:` + `"""` doc string | Raw text, sent as written. Can be malformed, for negative tests. |
| `Given a new "update" request from the "read" response` | Sends back what a GET returned, after edits. |
| `Given a new "item" request from the "order" response field "items[0]"` | Copies one part of a response. |

### Change it, over as many steps as needed

| Step | Notes |
|---|---|
| `And the "order" request field "customer.address.city" is set to "Graz"` | Nested paths are created if missing. |
| `And the "order" request fields are set:` + table | Several paths in one step. |
| `And the "order" request field "items[1]" is removed` | Fields or array elements. Fails if there is nothing there, to catch typos. |
| `And the "order" request is merged with:` + JSON doc string | Deep merge (JSON Merge Patch): objects merge, `null` removes a field, anything else replaces it. Good for changing many nested values of a large payload at once. |
| `And the "order" request header "X-Correlation-Id" is "run-{unique}"` | Headers for this request only. They win over the service's default headers. |
| `Then the "order" request field "items[-1].quantity" is "3"` | Checks the draft before sending. |

### Send it

| Step | Notes |
|---|---|
| `When I send the "order" request as POST to "/orders" on the "orders" service` | Any method: POST, PUT, PATCH, DELETE, … The response is stored as `"order"`. |
| `When I send GET "/orders/${orderId}" to the "orders" service as "orderRead"` | A call without a body, stored under the given name. |
| `And "/orders/${orderId}" on the "orders" service is deleted after the scenario` | Cleanup. Variables are filled in when the step runs. |

Every call also becomes the "last response", so `Then the API responds with status 201` still works.

### Check and chain responses

| Step | Notes |
|---|---|
| `Then the "order" response status is 201` | |
| `Then the "order" response field "items[0].sku" is "SKU-1"` | |
| `Then the "order" response fields are:` + table | Reports every mismatch at once. |
| `Then the "list" response field "$" has 3 items` | Size of an array (or object). `$` is the whole body. |
| `And I save the "order" response field "id" as "orderId"` | Use it later as `${orderId}`. |
| `And the variable "region" is "eu-{unique}"` | A fixed value that several requests share. |
| `Given a unique number is saved as "orderNo"` | A number no other call returns in this run, even in parallel. Use it as `${orderNo}`. |

### Set a zone id in one step, read it in the next

[`ZoneSteps`](../src/test/java/com/agenticqa/steps/ZoneSteps.java) sets a unique zone id (a number, unique across
parallel scenarios) and keeps it in two places: the `TestSession` under `Keys.ZONE_ID` for Java steps in any step
class, and the variable `${zoneId}` for feature files. See
[`zone_id.feature`](../src/test/resources/features/api/zone_id.feature).

```gherkin
Given a unique zone id is set
When I read the zone id
Then the zone id read is the one that was set
And the "zone" request field "zoneId" is set to "json:${zoneId}"
```

| Step | Notes |
|---|---|
| `Given a unique zone id is set` | New number, e.g. `1794511591661`. |
| `Given the zone id is "${newZone}"` | Sets a given value, for example one a response returned. |
| `When I read the zone id` | Reads it back from the session; fails clearly if no step set one. |
| `Then the zone id read is the one that was set` | Same value in the session and in `${zoneId}`. |
| `Then the zone id should be "..."`, `Then the zone id is not "..."` | Checks. |
| `And I save the zone id as "firstZone"` | Copies it into another variable. |

In Java, any step class of the scenario reads it with `long zoneId = session.get(Keys.ZONE_ID);`.

### Bridge to the customer and GUI steps

| Step | Notes |
|---|---|
| `Given the "hedy" response is the customer` | API to GUI: the response becomes "the customer", so `I search the customers page for that customer` works on it. The customer is deleted after the scenario. |
| `And I save the customer's id as "radiaId"` | GUI to API: works for a customer added through the form. Also `name`, `email` and `status`. |

The customer steps (`a new customer request from …`, `the request field … is set to …`, `I send the create
customer request`) use the named request `"customer"`. The generic steps can therefore also edit it and read its response.

## Endpoint catalog: call endpoints by name

With hundreds of endpoints, a Java class, record or method per request does not scale. Here an endpoint is one
line of YAML, and feature files and Java code call it by name. Endpoint paths are the same in every
environment, so the catalog lives once, under `endpoints:` in
[`src/main/resources/config/common.yaml`](../src/main/resources/config/common.yaml), one group per service:

```yaml
endpoints:
  customers:                         # group name: the part before the dot in "customers.get"
    service: customers               # the name under 'services:' in the environment file
    endpoints:
      list:   { method: GET,    path: /customers }
      get:    { method: GET,    path: "/customers/{id}" }
      create: { method: POST,   path: /customers,        body: customers/new_customer.json }
      update: { method: PUT,    path: "/customers/{id}", body: customers/new_customer.json }
      delete: { method: DELETE, path: "/customers/{id}" }
```

The name is `<group>.<key>`, for example `customers.create`. `{id}` is a path parameter, URL-encoded when it is
filled. `body` is an optional default body (a file under `src/test/resources/testdata`). The base URL, auth
(client id and secret), headers and timeout come from the environment file, so one catalog serves dev, test
and UAT.

### Steps

| Step | Notes |
|---|---|
| `Given a new "ada" request for "customers.create"` | Starts from the endpoint's default body, or `{}`. Then edit it with the usual field steps. |
| `And the "ada" request path parameter "id" is "${adaId}"` | Path parameter for a later call with this request. |
| `When I call "customers.create" with the "ada" request` | Method, path and service from the catalog. The response is stored as `"ada"`. |
| `When I call "customers.get" as "adaRead" with:` + table `\| id \| ${adaId} \|` | No body; path parameters from the table. |
| `When I call "customers.list" as "all"` | No body, no table. |

A path parameter not given explicitly falls back to the variable with the same name: after
`I save the "x" response field "id" as "id"`, `When I call "customers.get" as "read"` fills `{id}`. A missing
parameter fails and names it. An unknown endpoint lists the endpoints of that group. See
[`endpoint_catalog.feature`](../src/test/resources/features/api/endpoint_catalog.feature).

### In Java: `EndpointClient` and `Payload`

Step code needs no request class either:

```java
EndpointClient api = new EndpointClient(session);

Payload body = Payload.file("customers/new_customer.json")     // or Payload.empty(), Payload.of(map or record)
        .set("name", "Ada " + TestData.token())
        .set("profile.addresses[0].city", "Graz")               // nested paths are created
        .set("limits.creditEur", 7500)                          // numbers, booleans, lists, maps
        .remove("status");
APIResponse created = api.call("customers.create", body);
long id = Payload.of(created).get("id").asLong();               // read responses by path

APIResponse updated = api.call("customers.update", Map.of("id", id),
        Payload.of(created).remove("id").set("status", "inactive"));
```

`Payload` uses the same paths as the feature-file steps (below). Jackson serializes it as its JSON, so it can
be passed wherever a body object is expected. A typed record such as `Customer` is only worth keeping for the
few responses that steps compare field by field. It needs no factory or `withX` methods.

### Request POJOs

A request body can also be a plain Java object (POJO). `EndpointClient` serializes any object with Jackson, so
a POJO needs no extra code to be sent. Example:
[`CustomerRequest`](../src/main/java/com/agenticqa/api/model/CustomerRequest.java).

```java
CustomerRequest body = new CustomerRequest()
        .setName("Ada Lovelace " + TestData.token())
        .setEmail("ada@example.test")
        .setStatus("inactive");                          // fields left null are not sent
APIResponse created = api.call("customers.create", body);
Customer customer = ApiClient.read(created, Customer.class);   // the response, typed

// a negative test from the same POJO: Payload takes over where the type cannot
api.call("customers.create", Payload.of(body).set("status", 42));
```

Rules for a request POJO: a no-argument constructor, getters and setters (setters may return `this` for
chaining), `@JsonInclude(NON_NULL)` so unset fields are not sent, and `@JsonIgnoreProperties(ignoreUnknown = true)`
so it can also read a response. Put them in `src/main/java/com/agenticqa/api/model`.

In feature files, [`CustomerPojoSteps`](../src/test/java/com/agenticqa/steps/CustomerPojoSteps.java) keeps the POJO
in the session and sends it ([`customer_pojo_requests.feature`](../src/test/resources/features/api/customer_pojo_requests.feature)):

```gherkin
Given a customer request object:
  | name  | Grace Hopper {unique}              |
  | email | grace.hopper.{unique}@example.test |
And the customer request object status is "inactive"
When I call "customers.create" with the customer request object as "pojo"
Then the "pojo" response status is 201
And the "pojo" response matches the customer request object
```

POJO or `Payload`? Use a POJO when a body is stable, mostly built in Java, and its type helps (autocomplete,
compile errors on renamed fields). Use a JSON template plus `Payload` or the generic field steps when a body is
large, changes often, or tests vary it field by field (missing fields, wrong types), which a POJO cannot express.
Both go through the same `EndpointClient.call`, so they can be mixed.

**What to write per new endpoint:**

| You need | Write |
|---|---|
| a new endpoint | one line under its group in `endpoints:` of `config/common.yaml` |
| a typical body for it | a JSON file under `testdata/`, referenced as `body:` |
| a typed body used from Java | a POJO in `api/model` (optional) |
| variations (missing field, wrong type, nested change) | nothing: field steps in the feature file, or `Payload.set/remove` |
| a new service | one entry under `services:` per environment file (URL, auth), and a new group under `endpoints:` in `common.yaml` |
| a readable business step used in many scenarios | a step method using `EndpointClient` + `Payload` or a POJO (optional) |

## Paths and values

| Path | Meaning |
|---|---|
| `name`, `profile.address.city` | object fields |
| `items[0].qty`, `items[-1]` | array index; `-1` is the last element |
| `tags[]`, `items[].sku` | append (when setting); the second form appends a new object |
| `["odd.key"]` | a field name containing dots or brackets |
| `$` or `$.a.b` | the whole body / an optional `$.` prefix |

Values are **text** by default. Prefix `json:` for anything else: `json:3`, `json:true`, `json:null`,
`json:{"a":1}` or `json:["x","y"]`. Expectations work the same way: a plain `3` matches the number 3 or the text
"3", while `json:3` matches only the number.

`{unique}` is one random token per scenario, the same everywhere it appears. `${name}` is a saved variable. Both
are filled in for values, files, doc strings, URLs, headers and expectations. An unknown `${name}` fails and
lists the variables that exist.

## When something fails

If a scenario fails, its HTTP calls are attached to the report as `api-calls`, in order. Each entry shows the
method, URL, status, time, and the request and response bodies, cut to 4000 characters each. In a long
multi-service scenario this shows exactly which call went wrong. See [reporting.md](reporting.md).

## Limits

- The sample app is a single service (`customers`), so the example scenarios address only that one. A scenario
  across real microservices looks the same, with more `services:` entries and service names in the steps.
- Bodies are JSON. Form-encoded, multipart and XML bodies would need their own steps.
- Paths are a small, strict syntax (above), not full JSONPath: there are no filters or wildcards.
