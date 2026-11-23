package com.induztek.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class IdentityClientConfiguration {
    // Sin @LoadBalanced: los servicios se resuelven por URL fija (propiedades services.*.url),
    // no por Eureka. Prototype: cada cliente recibe su propio builder.
    @Bean
    @Scope("prototype")
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
