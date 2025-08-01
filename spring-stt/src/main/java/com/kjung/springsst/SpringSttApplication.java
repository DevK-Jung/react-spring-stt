package com.kjung.springsst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy
@SpringBootApplication
public class SpringSttApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSttApplication.class, args);
    }

}
