package com.owiseman.embedding.service;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
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
import java.util.Arrays;

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
                Path vocabPath = Paths.get(modelDirectory.getParent().toString(), "models/LaBSE_1/assets/cased_vocab.txt");
                logger.info("尝试加载词汇表: {}", vocabPath);
                
                tokenizer = new BertFullTokenizer(DefaultVocabulary.builder()
                        .optMinFrequency(1)
                        .optUnknownToken("[UNK]")
                        .addFromTextFile(vocabPath)
                        .build(), false);
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
     * @return 嵌入向量（浮点数数组）
     */
    public float[] getEmbedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                logger.warn("尝试对空文本进行向量化");
                return new float[0];
            }

            // 对文本进行预处理（如有必要）
            String processedText = preprocessText(text);

            // 使用模型预测获取嵌入向量
            float[] embedding = predictor.predict(processedText);

            // 对向量进行归一化处理
            normalizeVector(embedding);

            logger.debug("成功生成嵌入向量，维度: {}", embedding.length);
            return embedding;
        } catch (TranslateException e) {
            logger.error("生成嵌入向量失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法生成嵌入向量", e);
        }
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