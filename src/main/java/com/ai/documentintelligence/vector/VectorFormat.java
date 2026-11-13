package com.ai.documentintelligence.vector;

/** Converts embeddings to pgvector's text literal, e.g. {@code [0.1,0.2,0.3]}. */
public final class VectorFormat {

	private VectorFormat() {
	}

	public static String toLiteral(float[] vector) {
		if (vector == null || vector.length == 0) {
			throw new IllegalArgumentException("Embedding is empty");
		}
		StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
		for (int i = 0; i < vector.length; i++) {
			float v = vector[i];
			if (!Float.isFinite(v)) {
				throw new IllegalArgumentException("Embedding contains a non-finite value at index " + i);
			}
			if (i > 0) {
				sb.append(',');
			}
			sb.append(v);
		}
		return sb.append(']').toString();
	}
}
