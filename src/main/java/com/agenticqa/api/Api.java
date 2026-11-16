package com.agenticqa.api;

/**
 * An API class that {@link ApiFactory} can create, in the style of the existing framework: a public no-argument
 * constructor that reads its base URL with {@code Config.getApiUrl(name)}, and methods that call the service
 * with REST Assured and return its {@code Response}.
 * <p>
 * One instance of each API class is shared by the whole run, by every scenario and thread, so an implementation
 * must keep no per-scenario state: only final settings such as its base URL and paths.
 */
public interface Api {
}
