package com.textaudit.trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 模型训练服务主应用类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
public class ModelTrainerApplication {

    static {
        // 设置系统属性
        System.setProperty("java.awt.headless", "true");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("user.timezone", "Asia/Shanghai");
        
        // DL4J配置
        System.setProperty("org.bytedeco.javacpp.maxbytes", "8G");
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "12G");
    }

    public static void main(String[] args) {
        SpringApplication.run(ModelTrainerApplication.class, args);
    }
}