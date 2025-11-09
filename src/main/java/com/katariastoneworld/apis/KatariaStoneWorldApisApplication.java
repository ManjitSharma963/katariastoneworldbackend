package com.katariastoneworld.apis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KatariaStoneWorldApisApplication {

    public static void main(String[] args) {
        SpringApplication.run(KatariaStoneWorldApisApplication.class, args);
    }

}

