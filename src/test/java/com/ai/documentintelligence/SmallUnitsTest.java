package com.ai.documentintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ai.documentintelligence.vector.VectorFormat;

class SmallUnitsTest {

	@Test
	void formatsPgvectorLiteral() {
		assertThat(VectorFormat.toLiteral(new float[] { 0.5f, -1f, 2.25f })).isEqualTo("[0.5,-1.0,2.25]");
	}

	@Test
	void rejectsEmptyOrNonFiniteVectors() {
		assertThatThrownBy(() -> VectorFormat.toLiteral(new float[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VectorFormat.toLiteral(new float[] { Float.NaN }))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
