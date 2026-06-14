package com.example.llmnode.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
// ---- kduy fix here ---
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.time.Duration;
// ---- to here kduy ---

@Configuration
public class RestTemplateConfig {

    // ---- kduy fix here ---
    // @Bean
    // public RestTemplate restTemplate() {
    //     return new RestTemplate();
    // }
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
    // ---- to here kduy ---
}