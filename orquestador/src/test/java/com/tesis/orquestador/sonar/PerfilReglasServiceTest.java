package com.tesis.orquestador.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class PerfilReglasServiceTest {

	@Test
	void leeLasReglasSinComentariosNiDuplicados() {
		List<String> reglas = PerfilReglasService.reglasTesis();

		assertTrue(reglas.contains("java:S2077"));
		assertTrue(reglas.stream().allMatch(r -> r.matches("java:S\\d+")), reglas::toString);
		assertEquals(reglas.size(), new HashSet<>(reglas).size());
	}
}
