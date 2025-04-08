# 文本向量化插件 (Embedding Plugin)

基于DJL和PyTorch引擎的文本向量化插件，支持多语言处理，适用于无GPU环境。

## 功能特点

- 使用LaBSE (Language-agnostic BERT Sentence Embedding) 模型进行文本向量化
- 支持多语言文本处理
- 轻量级设计，适合CPU环境运行
- 符合plugin.proto规范，可与主应用无缝集成
- 提供REST API和gRPC接口

## 系统要求

- Java 21 或更高版本
- Maven 3.6 或更高版本
- 至少6GB可用内存

## 快速开始

### 构建项目

```bash
cd embedding-plugin
mvn clean package
```

### 运行插件

```bash
java -jar target/embedding-plugin-0.1.0.jar
```

默认情况下，插件将在以下端口启动：
- REST API: http://localhost:8081/api/embedding
- gRPC服务: localhost:8081

## 配置选项

可以通过修改`src/main/resources/application.properties`文件或使用命令行参数来自定义插件配置：

```properties
# 服务器配置
server.port=8081

# 插件配置
plugin.name=embedding-plugin
plugin.version=0.1.0
plugin.serverHost=localhost
plugin.serverPort=8080
plugin.pluginPort=8081
plugin.modelType=LaBSE
```

## API使用说明

### REST API

**获取文本嵌入向量**

```
POST /api/embedding
Content-Type: application/json

{
  "text": "需要向量化的文本内容"
}
```

响应示例：

```json
{
  "embedding": [0.123, 0.456, ...],
  "dimensions": 768
}
```

### 通过主应用调用

主应用可以通过以下方式调用插件：

```java
RestTemplate restTemplate = new RestTemplate();
Map<String, Object> request = Map.of("text", text);
Map<String, Object> response = restTemplate.postForObject(EMBEDDING_SERVICE_URL, request, Map.class);

if (response != null && response.containsKey("embedding")) {
    List<Number> embeddingList = (List<Number>) response.get("embedding");
    float[] embedding = new float[embeddingList.size()];
    
    for (int i = 0; i < embeddingList.size(); i++) {
        embedding[i] = embeddingList.get(i).floatValue();
    }
    
    return embedding;
}
```

## 故障排除

- **内存不足错误**: 增加JVM堆内存 `-Xmx4g`
- **模型加载失败**: 检查网络连接，确保可以访问模型URL
- **插件注册失败**: 确保主服务器正在运行并可访问

## 许可证

[Apache License 2.0](LICENSE)