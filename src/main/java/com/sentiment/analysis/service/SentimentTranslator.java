package com.sentiment.analysis.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;

import java.util.Arrays;

/**
 * Converts text into the two inputs of the exported XLM-R ONNX model (input_ids, attention_mask)
 * and returns the raw output scores (logits) as a float[].
 *
 * Softmax and label mapping are done in plain Java by DjlSentimentService. DJL's built-in
 * TextClassificationTranslator is not used because its post-processing fails in DJL 0.36
 * ("argSort: Not implemented").
 *
 * This object is shared by all predictors, so it keeps no per-request state.
 */
public class SentimentTranslator implements NoBatchifyTranslator<String, float[]> {

    private final HuggingFaceTokenizer tokenizer;
    private final int maxLength;

    public SentimentTranslator(HuggingFaceTokenizer tokenizer, int maxLength) {
        this.tokenizer = tokenizer;
        this.maxLength = maxLength;
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        Encoding encoding = tokenizer.encode(input);
        long[] ids = encoding.getIds();
        long[] mask = encoding.getAttentionMask();

        if (ids.length > maxLength) {
            // Keep the first (maxLength - 1) tokens and the final end-of-sequence token.
            long[] cutIds = Arrays.copyOf(ids, maxLength);
            cutIds[maxLength - 1] = ids[ids.length - 1];
            ids = cutIds;
            mask = new long[maxLength];
            Arrays.fill(mask, 1L);
        }

        NDManager manager = ctx.getNDManager();
        // Shape (1, sequenceLength): a batch containing one sentence.
        NDArray inputIds = manager.create(ids, new Shape(1, ids.length));
        inputIds.setName("input_ids");
        NDArray attentionMask = manager.create(mask, new Shape(1, mask.length));
        attentionMask.setName("attention_mask");
        return new NDList(inputIds, attentionMask);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // Output 0 has shape (1, numLabels); flattened that is just numLabels floats.
        return list.get(0).toFloatArray();
    }
}
