package com.agenticqa.api;

/**
 * An API client that {@link ApiFactory} can create. Implementations need a public constructor
 * {@code (TestSession session)}, which the factory calls with the scenario's session, and must keep no state of
 * their own beyond that session, because one instance serves every step of the scenario.
 */
public interface Api {
}
