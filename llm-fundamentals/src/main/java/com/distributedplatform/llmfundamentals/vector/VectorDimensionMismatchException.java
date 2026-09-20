package com.distributedplatform.llmfundamentals.vector;

public class VectorDimensionMismatchException extends RuntimeException {

    private final int expected;
    private final int actual;

    public VectorDimensionMismatchException(int expected, int actual) {
        super(String.format("Vector dimension mismatch: expected %d, got %d", expected, actual));
        this.expected = expected;
        this.actual = actual;
    }

    public int getExpected() {
        return expected;
    }

    public int getActual() {
        return actual;
    }
}
