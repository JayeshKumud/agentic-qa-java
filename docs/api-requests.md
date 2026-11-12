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
| [`Endpoints`](../src/main/java/com/agenticqa/api/Endpoints.java) | The endpoint catalog: endpoints declared in YAML, called by name (`customers.get`). |
| [`EndpointClient`](../src/main/java/com/agenticqa/api/EndpointClient.java) | Calls any catalog endpoint from Java: `call("customers.get", Map.of("id", 42))`. |
| [`Payload`](../src/main/java/com/agenticqa/api/Payload.java) | Any JSON request body in Java, built from a file, map, record or response and edited by path. |
| [`HttpSteps`](../src/test/java/com/agenticqa/steps/HttpSteps.java) | The generic Gherkin steps listed below. |
| [`ServiceCleanup`](../src/test/java/com/agenticqa/steps/ServiceCleanup.java) | DELETEs registered resources after the scenario, newest first, whether it passed or failed. |
| [`CustomerRequestSteps`](../src/test/java/com/agenticqa/steps/CustomerRequestSteps.java) | The customer steps, plus the **bridge** between the generic steps and the customer API and GUI steps. |

## Services (microservices)

Every service is configured by name in `config/env/<env>.yaml`:

```yaml
services:
  customers:
    baseUrl: http://127.0.0.1:8000
    headers:
      Accept: application/json
  orders:                          # a second microservice is just another entry
    baseUrl: http://127.0.0.1:8001
    timeoutMs: 30000               # optional, defaults to api.timeoutMs
    headers:
      X-Api-Key: change-me         # sent with every request to this service
```

Steps address a service by its name (`on the "orders" service`). You can override a URL per run with
`-Dservices.orders.baseUrl=https://orders.staging.example`. An unknown name fails with the list of configured
services. The endpoint files (below) name their service the same way.

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

### Bridge to the customer and GUI steps

| Step | Notes |
|---|---|
| `Given the "hedy" response is the customer` | API to GUI: the response becomes "the customer", so `I search the customers page for that customer` works on it. The customer is deleted after the scenario. |
| `And I save the customer's id as "radiaId"` | GUI to API: works for a customer added through the form. Also `name`, `email` and `status`. |

The customer steps (`a new customer request from …`, `the request field … is set to …`, `I send the create
customer request`) use the named request `"customer"`. The generic steps can therefore also edit it and read its response.

## Endpoint catalog: call endpoints by name

With hundreds of endpoints, a Java class, record or method per request does not scale. Here an endpoint is one
line of YAML, and feature files and Java code call it by name. File per service:
[`src/main/resources/endpoints/customers.yaml`](../src/main/resources/endpoints/customers.yaml).

```yaml
service: customers                 # the name under 'services:' in the environment file
endpoints:
  list:   { method: GET,    path: /customers }
  get:    { method: GET,    path: "/customers/{id}" }
  create: { method: POST,   path: /customers,        body: customers/new_customer.json }
  update: { method: PUT,    path: "/customers/{id}", body: customers/new_customer.json }
  delete: { method: DELETE, path: "/customers/{id}" }
```

The name is `<file>.<key>`, for example `customers.create`. `{id}` is a path parameter, URL-encoded when it is
filled. `body` is an optional default body (a file under `src/test/resources/testdata`). The base URL,
headers and timeout come from the environment file, so one catalog serves dev, test and UAT.

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
parameter fails and names it. An unknown endpoint lists the endpoints of that file. See
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

**What to write per new endpoint:**

| You need | Write |
|---|---|
| a new endpoint | one line in `endpoints/<service>.yaml` |
| a typical body for it | a JSON file under `testdata/`, referenced as `body:` |
| variations (missing field, wrong type, nested change) | nothing: field steps in the feature file, or `Payload.set/remove` |
| a new service | one entry under `services:` per environment file, and a new `endpoints/<service>.yaml` |
| a readable business step used in many scenarios | a step method using `EndpointClient` + `Payload` (optional) |

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
