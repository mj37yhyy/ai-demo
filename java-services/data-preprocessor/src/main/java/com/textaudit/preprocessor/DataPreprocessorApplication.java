package com.textaudit.preprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据预处理服务主应用类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
public class DataPreprocessorApplication {

    public static void main(String[] args) {
        // 设置系统属性
        System.setProperty("java.awt.headless", "true");
        System.setProperty("file.encoding", "UTF-8");
        
        // 启动应用
        SpringApplication.run(DataPreprocessorApplication.class, args);
    }
}