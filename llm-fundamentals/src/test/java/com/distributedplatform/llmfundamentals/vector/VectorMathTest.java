package com.distributedplatform.llmfundamentals.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorMathTest {

    @Test
    void dotProduct_withValidVectors_computesCorrectDotProduct() {
        // Hand-computed expected value:
        // a = [1.0, 3.0, -5.0]
        // b = [4.0, -2.0, -1.0]
        // dotProduct = (1.0 * 4.0) + (3.0 * -2.0) + (-5.0 * -1.0)
        //            = 4.0 - 6.0 + 5.0
        //            = 3.0
        double[] a = {1.0, 3.0, -5.0};
        double[] b = {4.0, -2.0, -1.0};

        double result = VectorMath.dotProduct(a, b);

        assertThat(result).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void dotProduct_whenBothVectorsEmpty_throwsEmptyVectorException() {
        double[] a = {};
        double[] b = {};

        assertThatThrownBy(() -> VectorMath.dotProduct(a, b))
                .isInstanceOf(EmptyVectorException.class)
                .hasMessage("Vector must not be empty");
    }

    @Test
    void dotProduct_whenFirstVectorEmptyAndSecondNonEmpty_throwsEmptyVectorException() {
        // Confirms precedence: EmptyVectorException takes precedence over VectorDimensionMismatchException
        double[] a = {};
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.dotProduct(a, b))
                .isInstanceOf(EmptyVectorException.class)
                .hasMessage("Vector must not be empty");
    }

    @Test
    void dotProduct_whenFirstVectorNonEmptyAndSecondEmpty_throwsEmptyVectorException() {
        // Confirms precedence: EmptyVectorException takes precedence over VectorDimensionMismatchException
        double[] a = {1.0, 2.0};
        double[] b = {};

        assertThatThrownBy(() -> VectorMath.dotProduct(a, b))
                .isInstanceOf(EmptyVectorException.class)
                .hasMessage("Vector must not be empty");
    }

    @Test
    void dotProduct_whenDimensionsMismatch_throwsVectorDimensionMismatchException() {
        double[] a = {1.0, 2.0, 3.0};
        double[] b = {4.0, 5.0};

        assertThatThrownBy(() -> VectorMath.dotProduct(a, b))
                .isInstanceOf(VectorDimensionMismatchException.class)
                .hasMessage("Vector dimension mismatch: expected 3, got 2");
    }

    @Test
    void dotProduct_whenFirstVectorNull_throwsNullPointerException() {
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.dotProduct(null, b))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vector 'a' must not be null");
    }

    @Test
    void dotProduct_whenSecondVectorNull_throwsNullPointerException() {
        double[] a = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.dotProduct(a, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vector 'b' must not be null");
    }

    @Test
    void magnitude_withValidVector_computesCorrectEuclideanNorm() {
        // Hand-computed expected value:
        // v = [3.0, 4.0]
        // magnitude = sqrt(3.0^2 + 4.0^2) = sqrt(9 + 16) = sqrt(25) = 5.0
        double[] v2d = {3.0, 4.0};
        assertThat(VectorMath.magnitude(v2d)).isCloseTo(5.0, within(1e-9));

        // 3D vector: v = [1.0, 2.0, 2.0]
        // magnitude = sqrt(1^2 + 2^2 + 2^2) = sqrt(1 + 4 + 4) = sqrt(9) = 3.0
        double[] v3d = {1.0, 2.0, 2.0};
        assertThat(VectorMath.magnitude(v3d)).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void magnitude_withZeroVector_returnsZero() {
        // Zero vector has a valid Euclidean length of 0.0
        double[] zeroVec = {0.0, 0.0, 0.0};
        assertThat(VectorMath.magnitude(zeroVec)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void magnitude_whenVectorEmpty_throwsEmptyVectorException() {
        double[] empty = {};

        assertThatThrownBy(() -> VectorMath.magnitude(empty))
                .isInstanceOf(EmptyVectorException.class)
                .hasMessage("Vector must not be empty");
    }

    @Test
    void magnitude_whenVectorNull_throwsNullPointerException() {
        assertThatThrownBy(() -> VectorMath.magnitude(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vector 'v' must not be null");
    }

    @Test
    void cosineSimilarity_withIdenticalDirection_returnsOne() {
        // Parallel vectors with different magnitudes:
        // a = [1.0, 2.0], magnitude = sqrt(5)
        // b = [2.0, 4.0], magnitude = sqrt(20) = 2 * sqrt(5)
        // dotProduct = (1*2) + (2*4) = 10
        // cosineSimilarity = 10 / (sqrt(5) * 2 * sqrt(5)) = 10 / 10 = 1.0
        double[] a = {1.0, 2.0};
        double[] b = {2.0, 4.0};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_withOppositeDirection_returnsMinusOne() {
        // Anti-parallel vectors:
        // a = [1.0, 0.0], magnitude = 1.0
        // b = [-5.0, 0.0], magnitude = 5.0
        // dotProduct = -5.0
        // cosineSimilarity = -5.0 / (1.0 * 5.0) = -1.0
        double[] a = {1.0, 0.0};
        double[] b = {-5.0, 0.0};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_withOrthogonalVectors_returnsZero() {
        // Perpendicular (90 degrees) vectors:
        // a = [1.0, 0.0], b = [0.0, 1.0]
        // dotProduct = (1*0) + (0*1) = 0
        // cosineSimilarity = 0 / (1 * 1) = 0.0
        double[] a = {1.0, 0.0};
        double[] b = {0.0, 1.0};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_withKnownAngle_computesExpectedCosine() {
        // Hand-computed 45-degree angle vectors:
        // a = [1.0, 1.0], magnitude = sqrt(2)
        // b = [0.0, 2.0], magnitude = 2.0
        // dotProduct = (1*0) + (1*2) = 2.0
        // cosineSimilarity = 2.0 / (sqrt(2) * 2.0) = 1.0 / sqrt(2) ≈ 0.7071067811865475
        double[] a = {1.0, 1.0};
        double[] b = {0.0, 2.0};

        double expected = 1.0 / Math.sqrt(2.0);
        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void cosineSimilarity_whenFirstVectorIsZero_throwsZeroVectorException() {
        double[] a = {0.0, 0.0};
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Vector must not be a zero vector (magnitude is 0)");
    }

    @Test
    void cosineSimilarity_whenSecondVectorIsZero_throwsZeroVectorException() {
        double[] a = {1.0, 2.0};
        double[] b = {0.0, 0.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Vector must not be a zero vector (magnitude is 0)");
    }

    @Test
    void cosineSimilarity_whenBothVectorsAreZero_throwsZeroVectorException() {
        double[] a = {0.0, 0.0};
        double[] b = {0.0, 0.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Vector must not be a zero vector (magnitude is 0)");
    }

    @Test
    void cosineSimilarity_whenDimensionsMismatch_throwsVectorDimensionMismatchException() {
        double[] a = {1.0, 2.0, 3.0};
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(VectorDimensionMismatchException.class)
                .hasMessage("Vector dimension mismatch: expected 3, got 2");
    }

    @Test
    void cosineSimilarity_whenVectorEmpty_throwsEmptyVectorException() {
        double[] a = {};
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(EmptyVectorException.class)
                .hasMessage("Vector must not be empty");
    }

    @Test
    void cosineSimilarity_whenVectorNull_throwsNullPointerException() {
        double[] b = {1.0, 2.0};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(null, b))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vector 'a' must not be null");

        double[] a = {1.0, 2.0};
        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vector 'b' must not be null");
    }
}
