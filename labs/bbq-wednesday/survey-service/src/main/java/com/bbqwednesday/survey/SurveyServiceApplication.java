package com.bbqwednesday.survey;

import com.bbqwednesday.survey.client.TallyClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@ImportHttpServices(group = "results-service", types = TallyClient.class)
public class SurveyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurveyServiceApplication.class, args);
    }

    // @LoadBalanced teaches the RestClient to resolve lb://results-service
    // through Eureka + Spring Cloud LoadBalancer instead of a fixed host:port.
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Primary
    RestClient.Builder defaultRestClientBuilder() {
        return RestClient.builder();
    }
}
