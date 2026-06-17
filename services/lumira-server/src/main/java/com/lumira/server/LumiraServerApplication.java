package com.lumira.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.lumira.auth.AuthServiceApplication;
import com.lumira.file.FileServiceApplication;
import com.lumira.job.JobExecutorApplication;
import com.lumira.localization.LocalizationServiceApplication;
import com.lumira.message.MessageServiceApplication;
import com.lumira.payment.PaymentServiceApplication;
import com.lumira.plugin.PluginServiceApplication;

@SpringBootApplication(scanBasePackages = {"com.lumira"})
@ComponentScan(
        basePackages = {"com.lumira"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        AuthServiceApplication.class,
                        FileServiceApplication.class,
                        JobExecutorApplication.class,
                        LocalizationServiceApplication.class,
                        MessageServiceApplication.class,
                        PaymentServiceApplication.class,
                        PluginServiceApplication.class,
                        com.lumira.saas.modules.ai.AiServiceApplication.class
                }
        )
)
@ConfigurationPropertiesScan("com.lumira")
@MapperScan(basePackages = "com.lumira", annotationClass = Mapper.class)
public class LumiraServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiraServerApplication.class, args);
    }
}
