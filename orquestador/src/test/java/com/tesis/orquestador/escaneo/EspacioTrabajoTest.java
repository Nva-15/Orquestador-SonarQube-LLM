package com.tesis.orquestador.escaneo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EspacioTrabajoTest {

	@Test
	void claveDesdeRutaWindows() {
		assertEquals("mi-proyecto", EspacioTrabajo.claveDesde("D:\\repos\\Mi Proyecto\\"));
	}

	@Test
	void claveDesdeUrlGit() {
		assertEquals("spring-petclinic", EspacioTrabajo.claveDesde("https://github.com/spring-projects/spring-petclinic.git"));
		assertEquals("commons-lang", EspacioTrabajo.claveDesde("git@github.com:apache/commons-lang.git"));
	}

	@Test
	void claveSoloNumericaRecibePrefijo() {
		assertEquals("repo-2024", EspacioTrabajo.claveDesde("C:/tmp/2024"));
	}

	@Test
	void detectaUrlGit() {
		assertTrue(EspacioTrabajo.esUrlGit("https://github.com/a/b"));
		assertTrue(EspacioTrabajo.esUrlGit("git@github.com:a/b.git"));
		assertFalse(EspacioTrabajo.esUrlGit("D:\\repos\\b"));
	}
}
