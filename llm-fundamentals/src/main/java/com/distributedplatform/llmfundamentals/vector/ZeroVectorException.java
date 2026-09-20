package com.distributedplatform.llmfundamentals.vector;

public class ZeroVectorException extends RuntimeException {

    public ZeroVectorException() {
        super("Vector must not be a zero vector (magnitude is 0)");
    }

}
