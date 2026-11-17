package com.agenticqa.apis;

import com.agenticqa.config.Settings;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The global REST Assured setup, applied once before the run by {@code TestRunner}. Every
 * {@code RestAssured.given()} in the API classes uses it, so no API class has to repeat it. The values come from
 * the {@code api} section of config/common.yaml.
 */
public final class RestAssuredSetup {
    private static final Logger logger = LoggerFactory.getLogger(RestAssuredSetup.class);

    private RestAssuredSetup() {}

    /**
     * Switches relaxed HTTPS validation on (if configured) and sets the HTTP client timeouts.
     *
     * @param api the api settings
     */
    public static void configure(Settings.Api api) {
        if (api.relaxedHttps()) RestAssured.useRelaxedHTTPSValidation();
        RestAssured.config = RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", api.connectionTimeoutMs())
                .setParam("http.socket.timeout", api.socketTimeoutMs())
                .setParam("http.connection-manager.timeout", (long) api.connectionManagerTimeoutMs()));
        logger.info("REST Assured: relaxed HTTPS {}, timeouts connection {} ms, socket {} ms, connection manager {} ms",
                api.relaxedHttps() ? "on" : "off", api.connectionTimeoutMs(), api.socketTimeoutMs(),
                api.connectionManagerTimeoutMs());
    }
}
