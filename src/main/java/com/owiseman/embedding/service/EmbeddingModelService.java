package com.owiseman.embedding.service;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 嵌入模型服务
 * 负责加载和管理LaBSE模型，提供文本向量化功能
 */
@Service
public class EmbeddingModelService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingModelService.class);
    private static final String MODEL_NAME = "LaBSE";
    
    @Value("${plugin.modelPath:models/saved_model}")
    private String modelPath;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private BertFullTokenizer tokenizer;

    private static final int MAX_TEXT_LENGTH = 512; // 最大文本长度，超过此长度将进行分段处理

    /**
     * 初始化模型
     * 在应用启动时加载LaBSE模型
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("开始加载LaBSE模型...");
            
            Path modelDirectory = Paths.get(modelPath);
            logger.info("模型绝对路径: {}", modelDirectory);
            // 设置模型加载标准
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(modelDirectory)
                    .optModelName(MODEL_NAME)
                    .optEngine("PyTorch")
                    .optProgress(new ProgressBar())
                    .optTranslator(new TextEmbeddingTranslator())
                    .build();

            // 加载模型
            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();

            // 初始化分词器
            try {
                // 尝试从模型目录中加载词汇表
                HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Paths.get("models/LaBSE/tokenizer.json"));


            } catch (Exception e) {
                logger.warn("无法加载词汇表文件，将使用默认分词器: {}", e.getMessage());
            }

            logger.info("LaBSE模型加载完成");
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            logger.error("加载模型失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法加载嵌入模型", e);
        }
    }

    /**
     * 获取文本的嵌入向量
     * @param text 输入文本
     * @return 嵌入向量数组
     */
    public float[] getEmbedding(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }
        
        // 检查文本长度，如果过长则分段处理
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.warn("输入文本过长 ({}字符)，已截断至约{}个token", text.length(), MAX_TEXT_LENGTH);
            text = preprocessText(text);
        }
        
        try {
            // 直接使用predictor进行预测
            float[] result = predictor.predict(text);
            
            // 对结果进行归一化
            normalizeVector(result);
            
            logger.info("成功生成嵌入向量，维度: {}", result.length);
            return result;
        } catch (Exception e) {
            logger.error("生成嵌入向量失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 处理长文本的嵌入向量生成
     * 将长文本分段处理，然后合并结果
     */
    private float[] getEmbeddingForLongText(String text) throws Exception {
        // 分段处理的最大长度（字符数）
        final int SEGMENT_LENGTH = MAX_TEXT_LENGTH / 2;
        
        // 分段
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < text.length(); i += SEGMENT_LENGTH) {
            int end = Math.min(i + SEGMENT_LENGTH, text.length());
            segments.add(text.substring(i, end));
        }
        
        logger.info("长文本已分为{}段进行处理", segments.size());
        
        // 处理每个分段
        List<float[]> embeddings = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            logger.info("处理第{}段文本，长度: {}", i + 1, segment.length());
            
            try {
                // 直接使用predictor进行预测
                float[] embedding = predictor.predict(segment);
                
                // 对结果进行归一化
                normalizeVector(embedding);
                
                // 添加到结果列表
                embeddings.add(embedding);
            } catch (Exception e) {
                logger.error("处理第{}段文本时出错: {}", i + 1, e.getMessage());
                throw e;
            }
        }
        
        // 合并所有分段的嵌入向量（取平均值）
        if (embeddings.isEmpty()) {
            throw new RuntimeException("没有成功处理任何文本分段");
        }
        
        // 获取向量维度
        int dimension = embeddings.get(0).length;
        float[] result = new float[dimension];
        
        // 计算所有分段向量的平均值
        for (float[] embedding : embeddings) {
            for (int i = 0; i < dimension; i++) {
                result[i] += embedding[i] / embeddings.size();
            }
        }
        
        // 对最终结果进行归一化
        normalizeVector(result);
        
        logger.info("成功合并{}段文本的嵌入向量，维度: {}", embeddings.size(), dimension);
        return result;
    }

    /**
     * 对输入文本进行预处理
     * @param text 原始文本
     * @return 预处理后的文本
     */
    private String preprocessText(String text) {
        // 简单的预处理：去除多余空格，限制长度等
        text = text.trim().replaceAll("\\s+", " ");

        // LaBSE模型通常有输入长度限制，这里可以根据需要截断
        int maxLength = 512; // BERT类模型通常最大支持512个token
        if (tokenizer != null && text.length() > maxLength * 2) { // 粗略估计
            text = text.substring(0, maxLength * 2);
            logger.warn("输入文本过长，已截断至约{}个token", maxLength);
        }

        return text;
    }

    /**
     * 对向量进行L2归一化
     * @param vector 需要归一化的向量
     */
    private void normalizeVector(float[] vector) {
        float squareSum = 0.0f;
        for (float value : vector) {
            squareSum += value * value;
        }

        if (squareSum > 0) {
            float norm = (float) Math.sqrt(squareSum);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / norm;
            }
        }
    }

    /**
     * 关闭模型和预测器
     * 在应用关闭时释放资源
     */
    @PreDestroy
    public void close() {
        logger.info("关闭嵌入模型资源...");
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }
}