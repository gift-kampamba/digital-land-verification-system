package com.landverification;

import com.landverification.config.AppProperties;
import com.landverification.config.JwtProperties;
import com.landverification.config.BlockchainProperties;
import com.landverification.config.Web3jProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class, BlockchainProperties.class, Web3jProperties.class})
public class LandVerificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(LandVerificationApplication.class, args);
    }
}

