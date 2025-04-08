package com.owiseman.embedding.service;

import com.owiseman.dataapi.proto.HeartbeatRequest;
import com.owiseman.dataapi.proto.PluginRegistration;
import com.owiseman.dataapi.proto.PluginServiceGrpc;
import com.owiseman.dataapi.proto.RegistrationResponse;
import com.owiseman.dataapi.proto.GetPluginByNameRequest;
import com.owiseman.dataapi.proto.GetPluginByNameResponse;
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
    private static final int MAX_RETRIES = 3; // 最大重试次数

    private final PluginProperties properties;
    private final EmbeddingPlugin embeddingPlugin;
    
    private ManagedChannel channel;

    private PluginServiceGrpc.PluginServiceBlockingStub blockingStub;
    private String pluginId;
    private boolean registered = false;
    private long lastSuccessfulHeartbeat = 0;

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
        initGrpcChannel();
    }

    /**
     * 初始化gRPC通道
     */
    private void initGrpcChannel() {
        try {
            // 关闭旧通道
            if (channel != null && !channel.isShutdown()) {
                logger.info("关闭旧的gRPC通道");
                try {
                    channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.warn("关闭旧通道时中断", e);
                    Thread.currentThread().interrupt();
                }
            }
            
            // 创建gRPC通道
            String serverAddress = properties.getServerHost() + ":" + properties.getServerPort();
            logger.info("连接到主服务器: {}", serverAddress);
            
            channel = ManagedChannelBuilder.forTarget(serverAddress)
                    .usePlaintext() // 开发环境使用明文传输，生产环境应使用TLS
                    .maxInboundMessageSize(10 * 1024 * 1024) // 10MB
                    // 添加更多连接参数
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .build();
            
            blockingStub = PluginServiceGrpc.newBlockingStub(channel)
                    .withMaxInboundMessageSize(10 * 1024 * 1024) // 10MB
                    .withDeadlineAfter(15, TimeUnit.SECONDS); // 设置默认超时时间
            
            // 注册插件
            registerPlugin();
        } catch (Exception e) {
            logger.error("初始化插件注册服务失败: {}", e.getMessage(), e);
            // 设置重试
            scheduleReconnect();
        }
    }

    /**
     * 安排重新连接
     */
    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                logger.info("5秒后尝试重新连接...");
                Thread.sleep(5000);
                initGrpcChannel();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 注册插件到主服务器
     */
    private void registerPlugin() {
      if (blockingStub == null) {
            logger.warn("blockingStub为空，尝试重新初始化gRPC通道");
            initGrpcChannel();

            if (blockingStub == null) {
                logger.error("无法初始化gRPC通道，跳过插件注册");
                return;
            }
        }

        try {
            String pluginName = properties.getName();
            logger.info("开始注册插件: {}", pluginName);

            // 先尝试通过名称查找插件
            GetPluginByNameRequest findRequest = GetPluginByNameRequest.newBuilder()
                    .setName(pluginName)
                    .build();

            try {
                GetPluginByNameResponse findResponse = blockingStub.getPluginByName(findRequest);
                if (findResponse.hasPlugin()) {
                    pluginId = findResponse.getPlugin().getPluginId();
                    logger.info("找到现有插件: {}, ID: {}", pluginName, pluginId);
                    return;
                }
            } catch (Exception e) {
                logger.warn("查找插件时出错: {}", e.getMessage());
            }

            // 如果找不到，则注册新插件
            // 确保提供正确的主机和端口
            String hostAddress = properties.getServerHost();
            if ("0.0.0.0".equals(hostAddress)) {
                hostAddress = "localhost"; // 如果绑定到所有接口，使用localhost作为注册地址
            }

            PluginRegistration registration = PluginRegistration.newBuilder()
                    .setName(pluginName)
                    .setVersion(properties.getVersion())
                    .setType("MQTT")
                    .setDescription("MQTT消息代理插件")
                    .setHost(hostAddress)
                    .setPort(properties.getPluginPort()) // 确保使用正确的gRPC端口
                    .build();

            RegistrationResponse response = blockingStub.registerPlugin(registration);

            if (response.getSuccess()) {
                pluginId = response.getPluginId();
                logger.info("插件注册成功，ID: {}", pluginId);
            } else {
                logger.error("插件注册失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            logger.error("注册插件时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 定期发送心跳
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (blockingStub == null) {
            logger.warn("blockingStub为空，尝试重新初始化gRPC通道");
            initGrpcChannel();
            return;
        }
        
        if (!registered || pluginId == null) {
            logger.warn("插件未注册，无法发送心跳");
            // 尝试重新注册
            registerPlugin();
            return;
        }
        
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                logger.debug("发送心跳... (尝试 {}/{})", retryCount + 1, MAX_RETRIES);
                
                // 创建心跳请求
                HeartbeatRequest request = HeartbeatRequest.newBuilder()
                        .setPluginId(pluginId)
                        .setStatusInfo(embeddingPlugin.getInfo().getStatus())
                        .build();
                
                // 发送心跳请求
                blockingStub.heartbeat(request);
                
                lastSuccessfulHeartbeat = System.currentTimeMillis();
                logger.debug("心跳发送成功");
                return;
            } catch (Exception e) {
                logger.error("发送心跳失败 (尝试 {}/{}): {}", retryCount + 1, MAX_RETRIES, e.getMessage());
                retryCount++;
                
                if (retryCount >= MAX_RETRIES) {
                    this.registered = false; // 标记为未注册，下次会尝试重新注册
                    // 如果连续失败，尝试重新初始化通道
                    initGrpcChannel();
                } else {
                    try {
                        // 等待一段时间再重试
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * 获取上次成功心跳的时间
     */
    public long getLastSuccessfulHeartbeat() {
        return lastSuccessfulHeartbeat;
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