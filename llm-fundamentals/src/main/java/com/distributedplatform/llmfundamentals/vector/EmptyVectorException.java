package com.distributedplatform.llmfundamentals.vector;

public class EmptyVectorException extends RuntimeException {

    public EmptyVectorException() {
        super("Vector must not be empty");
    }

}
