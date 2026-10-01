package com.nbamanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.python.timeout")
public class PythonTimeoutProperties {

    private int liveSeconds = 900;
    private int detailSeconds = 600;
    private int playoffSeconds = 1800;
    private int translationSeconds = 1800;
    private int bulkSeconds = 3600;

    public int getLiveSeconds() {
        return liveSeconds;
    }

    public void setLiveSeconds(int liveSeconds) {
        this.liveSeconds = liveSeconds;
    }

    public int getDetailSeconds() {
        return detailSeconds;
    }

    public void setDetailSeconds(int detailSeconds) {
        this.detailSeconds = detailSeconds;
    }

    public int getPlayoffSeconds() {
        return playoffSeconds;
    }

    public void setPlayoffSeconds(int playoffSeconds) {
        this.playoffSeconds = playoffSeconds;
    }

    public int getTranslationSeconds() {
        return translationSeconds;
    }

    public void setTranslationSeconds(int translationSeconds) {
        this.translationSeconds = translationSeconds;
    }

    public int getBulkSeconds() {
        return bulkSeconds;
    }

    public void setBulkSeconds(int bulkSeconds) {
        this.bulkSeconds = bulkSeconds;
    }
}
