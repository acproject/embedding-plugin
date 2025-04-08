package com.owiseman.embedding.grpc;

import com.owiseman.dataapi.proto.*;
import com.owiseman.dataapi.plugins.sdk.CommandResult;
import com.owiseman.embedding.plugin.EmbeddingPlugin;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件服务gRPC实现
 * 处理来自主应用的gRPC请求
 */
@Service
public class PluginServiceImpl extends PluginServiceGrpc.PluginServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(PluginServiceImpl.class);

    private final EmbeddingPlugin embeddingPlugin;

    @Autowired
    public PluginServiceImpl(EmbeddingPlugin embeddingPlugin) {
        this.embeddingPlugin = embeddingPlugin;
    }

    @Override
    public void executeCommand(CommandRequest request, StreamObserver<CommandResponse> responseObserver) {
        logger.info("收到命令执行请求: {}", request.getCommand());
        
        try {
            // 转换参数
            Map<String, String> params = new HashMap<>(request.getParametersMap());
            
            // 执行命令
            CommandResult result = embeddingPlugin.executeCommand(request.getCommand(), params);
            
            // 构建响应
            CommandResponse response = CommandResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setResult(result.getResult() != null ? result.getResult() : "")
                    .setErrorMessage(result.getErrorMessage() != null ? result.getErrorMessage() : "")
                    .build();
            
            // 发送响应
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("命令执行完成: {}, 成功: {}", request.getCommand(), result.isSuccess());
        } catch (Exception e) {
            logger.error("执行命令时发生错误: {}", e.getMessage(), e);
            
            // 发送错误响应
            CommandResponse errorResponse = CommandResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("执行命令时发生错误: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        logger.info("收到状态请求: {}", request.getPluginId());
        
        try {
            // 获取插件信息
            com.owiseman.dataapi.plugins.sdk.PluginInfo info = embeddingPlugin.getInfo();
            
            // 构建响应
            StatusResponse response = StatusResponse.newBuilder()
                    .setStatus(info.getStatus())
                    .setDetails("嵌入向量插件正常运行中")
                    .setUptime(System.currentTimeMillis()) // 简化处理，实际应记录启动时间
                    .build();
            
            // 发送响应
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("状态请求处理完成");
        } catch (Exception e) {
            logger.error("处理状态请求时发生错误: {}", e.getMessage(), e);
            
            // 发送错误响应
            StatusResponse errorResponse = StatusResponse.newBuilder()
                    .setStatus("ERROR")
                    .setDetails("处理状态请求时发生错误: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void stopPlugin(StopRequest request, StreamObserver<StopResponse> responseObserver) {
        logger.info("收到停止插件请求: {}", request.getPluginId());
        
        try {
            // 停止插件
            boolean success = embeddingPlugin.stop();
            
            // 构建响应
            StopResponse response = StopResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(success ? "插件已停止" : "停止插件失败")
                    .build();
            
            // 发送响应
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("停止插件请求处理完成，结果: {}", success);
        } catch (Exception e) {
            logger.error("处理停止插件请求时发生错误: {}", e.getMessage(), e);
            
            // 发送错误响应
            StopResponse errorResponse = StopResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("处理停止插件请求时发生错误: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
}