package com.owiseman.embedding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 插件配置属性
 * 用于从配置文件中加载插件相关的配置信息
 */
@Component
@ConfigurationProperties(prefix = "plugin")
public class PluginProperties {

    private String name = "embedding-plugin";
    private String version = "0.1.0";
    private String description = "基于DJL和PyTorch的文本向量化插件，支持多语言";
    private String serverHost = "localhost";
    private int serverPort = 19090;
    private int pluginPort = 8081;
    private String modelType = "LaBSE";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getPluginPort() {
        return pluginPort;
    }

    public void setPluginPort(int pluginPort) {
        this.pluginPort = pluginPort;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
}