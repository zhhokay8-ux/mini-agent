package com.miniagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAgentApplication.class, args);
    }
}
