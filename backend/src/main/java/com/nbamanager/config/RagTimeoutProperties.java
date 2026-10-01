package com.nbamanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag.timeout")
public class RagTimeoutProperties {

    private int connectMillis = 10_000;
    private int readMillis = 600_000;

    public int getConnectMillis() {
        return connectMillis;
    }

    public void setConnectMillis(int connectMillis) {
        this.connectMillis = connectMillis;
    }

    public int getReadMillis() {
        return readMillis;
    }

    public void setReadMillis(int readMillis) {
        this.readMillis = readMillis;
    }
}
