package com.owiseman.embedding.service;

import com.owiseman.dataapi.proto.HeartbeatRequest;
import com.owiseman.dataapi.proto.PluginRegistration;
import com.owiseman.dataapi.proto.PluginServiceGrpc;
import com.owiseman.dataapi.proto.RegistrationResponse;
import com.owiseman.embedding.config.PluginProperties;
import com.owiseman.embedding.plugin.EmbeddingPlugin;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * 插件注册服务
 * 负责将插件注册到主服务器，并定期发送心跳
 */
@Service
@EnableScheduling
public class PluginRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(PluginRegistrationService.class);
    private static final int SHUTDOWN_TIMEOUT = 5; // 秒

    private final PluginProperties properties;
    private final EmbeddingPlugin embeddingPlugin;
    
    private ManagedChannel channel;
    private PluginServiceGrpc.PluginServiceBlockingStub blockingStub;
    private String pluginId;
    private boolean registered = false;

    @Autowired
    public PluginRegistrationService(PluginProperties properties, EmbeddingPlugin embeddingPlugin) {
        this.properties = properties;
        this.embeddingPlugin = embeddingPlugin;
    }

    /**
     * 初始化gRPC通道并注册插件
     */
    @PostConstruct
    public void init() {
        try {
            // 创建gRPC通道
            String serverAddress = properties.getServerHost() + ":" + properties.getServerPort();
            logger.info("连接到主服务器: {}", serverAddress);
            
            channel = ManagedChannelBuilder.forTarget(serverAddress)
                    .usePlaintext() // 开发环境使用明文传输，生产环境应使用TLS
                    .build();
            
            blockingStub = PluginServiceGrpc.newBlockingStub(channel);
            
            // 注册插件
            registerPlugin();
        } catch (Exception e) {
            logger.error("初始化插件注册服务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册插件到主服务器
     */
    private void registerPlugin() {
        try {
            logger.info("注册插件到主服务器...");
            
            // 创建注册请求
            PluginRegistration registration = PluginRegistration.newBuilder()
                    .setName(properties.getName())
                    .setVersion(properties.getVersion())
                    .setType("embedding")
                    .setDescription(properties.getDescription())
                    .setHost("localhost") // 使用本机地址，实际部署时应使用可访问的地址
                    .setPort(properties.getPluginPort())
                    .build();
            
            // 发送注册请求
            RegistrationResponse response = blockingStub.registerPlugin(registration);
            
            if (response.getSuccess()) {
                this.pluginId = response.getPluginId();
                this.registered = true;
                logger.info("插件注册成功，ID: {}", pluginId);
            } else {
                logger.error("插件注册失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            logger.error("注册插件时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 定期发送心跳
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (!registered || pluginId == null) {
            logger.warn("插件未注册，无法发送心跳");
            // 尝试重新注册
            registerPlugin();
            return;
        }
        
        try {
            logger.debug("发送心跳...");
            
            // 创建心跳请求
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setPluginId(pluginId)
                    .setStatusInfo(embeddingPlugin.getInfo().getStatus())
                    .build();
            
            // 发送心跳请求
            blockingStub.heartbeat(request);
            
            logger.debug("心跳发送成功");
        } catch (Exception e) {
            logger.error("发送心跳失败: {}", e.getMessage());
            this.registered = false; // 标记为未注册，下次会尝试重新注册
        }
    }

    /**
     * 关闭gRPC通道
     */
    @PreDestroy
    public void shutdown() {
        logger.info("关闭插件注册服务...");
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("关闭gRPC通道时被中断", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}