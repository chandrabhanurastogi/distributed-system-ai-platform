package com.distributedplatform.disputeservice.embedding;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toVectorLiteral(double[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Double.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
