package com.owiseman.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 向量化插件应用程序入口点
 * 使用Spring Boot启动应用程序并提供REST API服务
 */
@SpringBootApplication
public class EmbeddingPluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingPluginApplication.class, args);
    }
}