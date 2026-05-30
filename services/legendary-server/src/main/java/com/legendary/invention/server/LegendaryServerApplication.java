package com.legendary.invention.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;

@SpringBootApplication(scanBasePackages = {"com.legendary.invention"})
@MapperScan(basePackages = "com.legendary.invention", annotationClass = Mapper.class)
public class LegendaryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegendaryServerApplication.class, args);
    }
}
