package com.owiseman.embedding.plugin;

import com.owiseman.dataapi.plugins.sdk.CommandResult;
import com.owiseman.dataapi.plugins.sdk.PluginConfig;
import com.owiseman.dataapi.plugins.sdk.PluginInfo;
import com.owiseman.dataapi.plugins.sdk.PluginSDK;
import com.owiseman.embedding.service.EmbeddingModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 嵌入向量插件实现
 * 实现PluginSDK接口，提供文本向量化功能
 */
@Component
public class EmbeddingPlugin implements PluginSDK {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingPlugin.class);
    private static final String PLUGIN_TYPE = "embedding";
    private static final String COMMAND_GET_EMBEDDING = "getEmbedding";
    private static final String COMMAND_STATUS = "status";
    
    private final EmbeddingModelService embeddingService;
    private PluginInfo pluginInfo;
    private PluginConfig pluginConfig;
    private AtomicBoolean running = new AtomicBoolean(false);
    private long startTime;

    @Autowired
    public EmbeddingPlugin(EmbeddingModelService embeddingService) {
        this.embeddingService = embeddingService;
        this.pluginInfo = new PluginInfo();
        this.pluginInfo.setStatus("初始化中");
    }

    @PostConstruct
    public void init() {
        // 创建默认插件信息，等待正式初始化
        this.pluginInfo.setName("embedding-plugin");
        this.pluginInfo.setVersion("0.1.0");
        this.pluginInfo.setType(PLUGIN_TYPE);
        this.pluginInfo.setDescription("基于DJL和PyTorch的文本向量化插件，支持多语言");
        
        // 添加支持的命令
        this.pluginInfo.addSupportedCommand(COMMAND_GET_EMBEDDING);
        this.pluginInfo.addSupportedCommand(COMMAND_STATUS);
    }

    @Override
    public boolean initialize(PluginConfig config) {
        try {
            logger.info("初始化嵌入向量插件...");
            this.pluginConfig = config;
            
            // 更新插件信息
            this.pluginInfo.setId(config.getPluginId());
            this.pluginInfo.setName(config.getPluginName());
            this.pluginInfo.setVersion(config.getPluginVersion());
            this.pluginInfo.setStatus("已初始化");
            
            logger.info("嵌入向量插件初始化完成");
            return true;
        } catch (Exception e) {
            logger.error("初始化插件失败: {}", e.getMessage(), e);
            this.pluginInfo.setStatus("初始化失败");
            return false;
        }
    }

    @Override
    public boolean start() {
        try {
            logger.info("启动嵌入向量插件...");
            this.startTime = System.currentTimeMillis();
            this.pluginInfo.setStatus("运行中");
            this.running.set(true);
            logger.info("嵌入向量插件已启动");
            return true;
        } catch (Exception e) {
            logger.error("启动插件失败: {}", e.getMessage(), e);
            this.pluginInfo.setStatus("启动失败");
            return false;
        }
    }

    @Override
    public boolean stop() {
        try {
            logger.info("停止嵌入向量插件...");
            this.pluginInfo.setStatus("已停止");
            this.running.set(false);
            logger.info("嵌入向量插件已停止");
            return true;
        } catch (Exception e) {
            logger.error("停止插件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public PluginInfo getInfo() {
        return this.pluginInfo;
    }

    /**
     * 获取插件运行时间（毫秒）
     */
    public long getUptime() {
        if (!running.get() || startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public CommandResult executeCommand(String command, Map<String, String> params) {
        if (!running.get() && !COMMAND_STATUS.equals(command)) {
            return CommandResult.error("插件未运行");
        }
        
        logger.info("执行命令: {}, 参数: {}", command, params);
        
        try {
            switch (command) {
                case COMMAND_GET_EMBEDDING:
                    return handleGetEmbeddingCommand(params);
                case COMMAND_STATUS:
                    return handleStatusCommand();
                default:
                    logger.warn("不支持的命令: {}", command);
                    return CommandResult.error("不支持的命令: " + command);
            }
        } catch (Exception e) {
            logger.error("执行命令失败: {}", e.getMessage(), e);
            return CommandResult.error("执行命令失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取嵌入向量的命令
     * @param params 命令参数
     * @return 命令执行结果
     */
    private CommandResult handleGetEmbeddingCommand(Map<String, String> params) {
        String text = params.get("text");
        
        if (text == null || text.trim().isEmpty()) {
            return CommandResult.error("参数'text'不能为空");
        }
        
        try {
            // 获取嵌入向量
            float[] embedding = embeddingService.getEmbedding(text);
            
            // 将向量转换为JSON字符串
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"");
            jsonBuilder.append("embedding\": [");
            
            for (int i = 0; i < embedding.length; i++) {
                jsonBuilder.append(embedding[i]);
                if (i < embedding.length - 1) {
                    jsonBuilder.append(", ");
                }
            }
            
            jsonBuilder.append("], \"");
            jsonBuilder.append("dimensions\": ").append(embedding.length);
            jsonBuilder.append("}");
            
            return CommandResult.success(jsonBuilder.toString());
        } catch (Exception e) {
            logger.error("生成嵌入向量失败: {}", e.getMessage(), e);
            return CommandResult.error("生成嵌入向量失败: " + e.getMessage());
        }
    }

    /**
     * 处理状态查询命令
     * @return 命令执行结果
     */
    private CommandResult handleStatusCommand() {
        StringBuilder status = new StringBuilder();
        status.append("{");
        status.append("\"status\": \"").append(pluginInfo.getStatus()).append("\", ");
        status.append("\"running\": ").append(running.get()).append(", ");
        status.append("\"uptime\": ").append(getUptime()).append(", ");
        status.append("\"name\": \"").append(pluginInfo.getName()).append("\", ");
        status.append("\"version\": \"").append(pluginInfo.getVersion()).append("\"");
        status.append("}");
        
        return CommandResult.success(status.toString());
    }

    @Override
    public String handleMessage(String message) {
        // 简单的消息处理，可以根据需要扩展
        logger.info("收到消息: {}", message);
        return "消息已接收";
    }
    
    /**
     * 检查插件是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}