package com.owiseman.embedding.controller;

import com.owiseman.embedding.service.EmbeddingModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 嵌入向量控制器
 * 提供REST API接口，用于获取文本的嵌入向量
 */
@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingController.class);

    private final EmbeddingModelService embeddingService;

    @Autowired
    public EmbeddingController(EmbeddingModelService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 获取文本的嵌入向量
     * @param request 包含文本的请求体
     * @return 包含嵌入向量的响应
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> getEmbedding(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.trim().isEmpty()) {
            logger.warn("接收到空文本请求");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "文本不能为空");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        try {
            logger.info("处理嵌入向量请求，文本长度: {}", text.length());
            
            // 获取嵌入向量
            float[] embedding = embeddingService.getEmbedding(text);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("embedding", embedding);
            response.put("dimensions", embedding.length);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("生成嵌入向量时发生错误: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "处理请求失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}