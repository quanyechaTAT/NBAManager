package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class RagTimeoutPropertiesTest {

    @Test
    void defaultsAllowLongStreamingAnswers() {
        RagTimeoutProperties properties = new RagTimeoutProperties();

        assertThat(properties.getConnectMillis()).isEqualTo(10_000);
        assertThat(properties.getReadMillis()).isEqualTo(600_000);
    }

    @Test
    void valuesCanBeOverridden() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.rag.timeout.connect-millis", "15000")
                .withProperty("app.rag.timeout.read-millis", "900000");

        RagTimeoutProperties properties = Binder.get(environment)
                .bind("app.rag.timeout", RagTimeoutProperties.class)
                .orElseThrow(() -> new AssertionError("RAG timeout properties were not bound"));

        assertThat(properties.getConnectMillis()).isEqualTo(15_000);
        assertThat(properties.getReadMillis()).isEqualTo(900_000);
    }
}
