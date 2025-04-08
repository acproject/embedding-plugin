package com.owiseman.embedding.config;

import com.owiseman.embedding.plugin.EmbeddingPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 插件配置类
 * 负责初始化插件配置并注册到Spring容器中
 */
@Configuration
public class EmbeddingPluginConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingPluginConfig.class);

    @Autowired
    private PluginProperties properties;

    @Autowired
    private EmbeddingPlugin embeddingPlugin;

    /**
     * 创建并初始化插件配置
     * @return 插件配置对象
     */
    @Bean
    public com.owiseman.dataapi.plugins.sdk.PluginConfig pluginConfig() {
        logger.info("创建插件配置...");

        com.owiseman.dataapi.plugins.sdk.PluginConfig config = new com.owiseman.dataapi.plugins.sdk.PluginConfig();
        config.setPluginName(properties.getName());
        config.setPluginVersion(properties.getVersion());
        config.setPluginType("embedding");
        config.setServerHost(properties.getServerHost());
        config.setServerPort(properties.getServerPort());

        // 添加额外配置
        config.addConfig("modelType", properties.getModelType());
        config.addConfig("pluginPort", String.valueOf(properties.getPluginPort()));

        // 初始化插件
        boolean initialized = embeddingPlugin.initialize(config);
        if (initialized) {
            logger.info("插件初始化成功");
            // 启动插件
            boolean started = embeddingPlugin.start();
            if (started) {
                logger.info("插件启动成功");
            } else {
                logger.error("插件启动失败");
            }
        } else {
            logger.error("插件初始化失败");
        }

        return config;
    }
}