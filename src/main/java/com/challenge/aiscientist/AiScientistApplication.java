package com.challenge.aiscientist;

import com.challenge.aiscientist.config.ChromaProperties;
import com.challenge.aiscientist.config.DashScopeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {DashScopeProperties.class, ChromaProperties.class})
public class AiScientistApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiScientistApplication.class, args);
    }
}
