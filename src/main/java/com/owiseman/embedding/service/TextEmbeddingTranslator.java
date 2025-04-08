package com.owiseman.embedding.service;

import ai.djl.modality.nlp.bert.BertTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * 文本嵌入转换器
 * 将输入文本转换为模型可处理的格式，并将模型输出转换为嵌入向量
 */
public class TextEmbeddingTranslator implements Translator<String, float[]> {

    private BertTokenizer tokenizer;

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        // 创建NDManager来管理NDArray资源
        NDManager manager = ctx.getNDManager();
        
        // 对输入文本进行预处理和编码
        // 注意：这里简化了处理过程，实际应用中可能需要更复杂的分词和编码
        long[] indices = new long[input.length()];
        for (int i = 0; i < input.length(); i++) {
            indices[i] = input.charAt(i);
        }
        
        // 创建输入张量
        NDArray indicesArray = manager.create(indices);
        
        // 返回处理后的输入
        return new NDList(indicesArray);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // 获取模型输出的嵌入向量
        NDArray embedding = list.get(0);
        
        // 如果输出是二维的，取第一个向量（CLS token的表示）
        if (embedding.getShape().dimension() > 1) {
            embedding = embedding.get(0);
        }
        
        // 将NDArray转换为float数组
        float[] result = embedding.toFloatArray();
        
        return result;
    }
}