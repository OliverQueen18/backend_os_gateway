package com.osgateway.ussd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.osgateway.ussd", "com.osgateway.common"})
public class UssdServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UssdServiceApplication.class, args);
    }
}