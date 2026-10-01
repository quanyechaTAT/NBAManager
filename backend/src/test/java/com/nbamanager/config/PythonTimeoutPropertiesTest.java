package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class PythonTimeoutPropertiesTest {

    @Test
    void defaults_allowSlowNetworkAndBulkScripts() {
        PythonTimeoutProperties properties = new PythonTimeoutProperties();

        assertThat(properties.getLiveSeconds()).isEqualTo(900);
        assertThat(properties.getDetailSeconds()).isEqualTo(600);
        assertThat(properties.getPlayoffSeconds()).isEqualTo(1800);
        assertThat(properties.getTranslationSeconds()).isEqualTo(1800);
        assertThat(properties.getBulkSeconds()).isEqualTo(3600);
    }

    @Test
    void valuesCanBeOverriddenFromApplicationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.python.timeout.live-seconds", "420")
                .withProperty("app.python.timeout.bulk-seconds", "2400");

        PythonTimeoutProperties properties = Binder.get(environment)
                .bind("app.python.timeout", PythonTimeoutProperties.class)
                .orElseThrow(() -> new AssertionError("timeout properties were not bound"));

        assertThat(properties.getLiveSeconds()).isEqualTo(420);
        assertThat(properties.getBulkSeconds()).isEqualTo(2400);
    }
}
