package com.nbamanager;

import com.nbamanager.config.EnvFileReader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NbaManagerApplication {

    public static void main(String[] args) {
        EnvFileReader.load();
        SpringApplication.run(NbaManagerApplication.class, args);
    }
}
