package com.tesis.orquestador.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecomendacionPasosTest {

	private static List<String> pasos(String texto) {
		Recomendacion r = new Recomendacion(1L, "claude-code", "haiku", "v1", "prompt");
		ReflectionTestUtils.setField(r, "recomendacion", texto);
		return r.getPasosRecomendacion();
	}

	@Test
	void separaPasosNumeradosEnUnSoloParrafo() {
		assertThat(pasos("1) Declara una constante para \"Bad Request\". 2) Sustituye las 5 apariciones. "
				+ "3) Mejor aún, usa HttpStatus.BAD_REQUEST.getReasonPhrase(). 4) Revisa \"timestamp\"."))
				.containsExactly("Declara una constante para \"Bad Request\".", "Sustituye las 5 apariciones.",
						"Mejor aún, usa HttpStatus.BAD_REQUEST.getReasonPhrase().", "Revisa \"timestamp\".");
	}

	@Test
	void aceptaPuntoYSaltosDeLinea() {
		assertThat(pasos("1. Primero.\n2. Segundo.")).containsExactly("Primero.", "Segundo.");
	}

	@Test
	void ignoraNumerosQueNoSonCorrelativos() {
		assertThat(pasos("1) Usa Java 21. 2) Cambia a la versión 5) solo si hace falta.")).containsExactly(
				"Usa Java 21.", "Cambia a la versión 5) solo si hace falta.");
	}

	@Test
	void textoSinNumeracionNoEsLista() {
		assertThat(pasos("Extrae el literal a una constante.")).isEmpty();
		assertThat(pasos("Hay 2) casos raros.")).isEmpty();
		assertThat(pasos("Antes de nada: 1) uno 2) dos")).isEmpty();
		assertThat(pasos(null)).isEmpty();
	}
}
