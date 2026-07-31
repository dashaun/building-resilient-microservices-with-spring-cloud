package com.bbqwednesday.survey.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Turns the {@link TallyClient} interface into a live proxy, backed by the
 * load-balanced RestClient so {@code http://results-service} resolves through
 * Eureka + Spring Cloud LoadBalancer.
 */
@Configuration
public class ClientConfig {

    @Bean
    TallyClient tallyClient(RestClient.Builder loadBalancedRestClientBuilder) {
        RestClient restClient = loadBalancedRestClientBuilder
                .baseUrl("http://results-service")
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(TallyClient.class);
    }
}
