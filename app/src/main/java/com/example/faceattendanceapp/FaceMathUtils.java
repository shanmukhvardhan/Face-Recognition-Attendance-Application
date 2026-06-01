package com.example.faceattendanceapp;

public class FaceMathUtils {

    public static float calculateEuclideanDistance(float[] liveEmbedding, float[] storedEmbedding) {
        if (liveEmbedding == null || storedEmbedding == null || liveEmbedding.length != storedEmbedding.length) {
            return Float.MAX_VALUE;
        }

        float sumOfSquaredDifferences = 0.0f;

        for (int i = 0; i < liveEmbedding.length; i++) {
            float difference = liveEmbedding[i] - storedEmbedding[i];
            sumOfSquaredDifferences += (difference * difference);
        }

        return (float) Math.sqrt(sumOfSquaredDifferences);
    }
}
