package com.agenticqa.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A typed view of a customer response, used where steps compare a customer field by field (for example a
 * table row on the page with what the API returned). It is only for reading: request bodies are
 * {@link Payload}s or request POJOs such as {@link com.agenticqa.api.model.CustomerRequest}, and unknown response fields
 * are ignored.
 * <p>
 * Most endpoints need no such record at all: read responses by path ({@code Payload.of(response).get("id")}
 * in Java, {@code the "x" response field "id" is ...} in feature files).
 *
 * @param id     server-assigned id
 * @param name   display name
 * @param email  email address
 * @param status for example "active"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Customer(Long id, String name, String email, String status) {}
