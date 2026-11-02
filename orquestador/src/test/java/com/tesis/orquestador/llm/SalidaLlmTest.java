package com.tesis.orquestador.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SalidaLlmTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void aceptaUnaRespuestaValida() {
		SalidaLlm s = SalidaLlm.validar("""
				{"veredicto":"FALSO_POSITIVO","confianza":0.85,"justificacion":"j","explicacion":"e",
				 "recomendacion":"r","codigoCorregido":""}""", json);

		assertEquals(Veredicto.FALSO_POSITIVO, s.veredicto());
		assertEquals(0.85, s.confianza());
	}

	@Test
	void rechazaConfianzaEnPorcentaje() {
		// Error real observado con un prompt mínimo: Haiku devolvió 92 en lugar de 0.92
		assertThrows(IllegalArgumentException.class, () -> SalidaLlm.validar("""
				{"veredicto":"VERDADERO_POSITIVO","confianza":92,"justificacion":"j","explicacion":"e",
				 "recomendacion":"r","codigoCorregido":""}""", json));
	}

	@Test
	void rechazaVeredictoDesconocidoYCamposVacios() {
		assertThrows(IllegalArgumentException.class, () -> SalidaLlm.validar("""
				{"veredicto":"QUIZAS","confianza":0.5,"justificacion":"j","explicacion":"e","recomendacion":"r"}""",
				json));
		assertThrows(IllegalArgumentException.class, () -> SalidaLlm.validar("""
				{"veredicto":"FALSO_POSITIVO","confianza":0.5,"justificacion":" ","explicacion":"e","recomendacion":"r"}""",
				json));
	}
}
