package com.owiseman.embedding.config;

import com.owiseman.embedding.grpc.PluginServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC服务器配置
 * 负责启动gRPC服务器并注册服务实现
 */
@Configuration
public class GrpcServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);
    private static final int SHUTDOWN_TIMEOUT = 5; // 秒

    private final PluginProperties properties;
    private final PluginServiceImpl pluginService;
    
    private Server server;

    @Autowired
    public GrpcServerConfig(PluginProperties properties, PluginServiceImpl pluginService) {
        this.properties = properties;
        this.pluginService = pluginService;
    }

    /**
     * 启动gRPC服务器
     */
    @PostConstruct
    public void startServer() {
        try {
            int port = properties.getPluginPort();
            logger.info("启动gRPC服务器，端口: {}", port);
            
            server = ServerBuilder.forPort(port)
                    .addService(pluginService)
                    .build()
                    .start();
            
            logger.info("gRPC服务器启动成功，监听端口: {}", port);
            
            // 添加JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("JVM关闭，停止gRPC服务器...");
                stopServer();
            }));
        } catch (IOException e) {
            logger.error("启动gRPC服务器失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法启动gRPC服务器", e);
        }
    }

    /**
     * 停止gRPC服务器
     */
    @PreDestroy
    public void stopServer() {
        if (server != null && !server.isShutdown()) {
            try {
                logger.info("停止gRPC服务器...");
                server.shutdown().awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
                logger.info("gRPC服务器已停止");
            } catch (InterruptedException e) {
                logger.warn("停止gRPC服务器时被中断", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}