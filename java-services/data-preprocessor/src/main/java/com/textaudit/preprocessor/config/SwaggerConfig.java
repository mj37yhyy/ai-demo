package com.textaudit.preprocessor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger API文档配置
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("文本审计系统 - 数据预处理服务")
                .description("提供文本清洗、分词、特征提取和数据增强等功能的微服务")
                .version("1.0.0")
                .contact(new Contact()
                    .name("TextAudit Team")
                    .email("support@textaudit.com")
                    .url("https://textaudit.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort + contextPath)
                    .description("本地开发环境"),
                new Server()
                    .url("http://data-preprocessor:8081" + contextPath)
                    .description("Docker容器环境"),
                new Server()
                    .url("https://api.textaudit.com" + contextPath)
                    .description("生产环境")
            ));
    }
}