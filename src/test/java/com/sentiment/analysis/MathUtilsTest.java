package com.sentiment.analysis;

import com.sentiment.analysis.util.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathUtilsTest {

    @Test
    void softmaxSumsToOneAndKeepsOrder() {
        double[] p = MathUtils.softmax(new float[]{1f, 2f, 3f});
        assertEquals(1.0, p[0] + p[1] + p[2], 1e-9);
        assertEquals(0.09003057, p[0], 1e-6);
        assertEquals(0.66524096, p[2], 1e-6);
        assertTrue(p[2] > p[1] && p[1] > p[0]);
    }

    @Test
    void softmaxIsStableForLargeValues() {
        double[] p = MathUtils.softmax(new float[]{1000f, 1001f, 1002f});
        assertEquals(1.0, p[0] + p[1] + p[2], 1e-9);
        assertTrue(!Double.isNaN(p[0]));
    }

    @Test
    void softmaxRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.softmax(new float[0]));
    }
}
