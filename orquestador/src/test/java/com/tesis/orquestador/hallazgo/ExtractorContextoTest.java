package com.tesis.orquestador.hallazgo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractorContextoTest {

	private static final String FUENTE = """
			package demo;

			public class Cuenta {

				private final java.util.Random azar = new java.util.Random();

				/** Calcula el total. */
				public int total(int a, String b) {
					int x = a * 2;
					return x;
				}

				public Cuenta() {
					System.out.println("hola");
				}
			}
			""";

	@Test
	void devuelveElMetodoContenedorConSuJavadoc() {
		Contexto c = ExtractorContexto.extraer(FUENTE, 9, 9);

		assertEquals("método total(int, String) de la clase Cuenta", c.descripcion());
		assertEquals(7, c.lineaInicio());
		assertTrue(c.codigo().startsWith("\t/** Calcula el total. */"));
		assertTrue(c.codigo().endsWith("\t}"));
	}

	@Test
	void unCampoDevuelveLaClaseCompletaParaVerSusUsos() {
		Contexto c = ExtractorContexto.extraer(FUENTE, 5, 5);

		assertEquals("clase Cuenta completa", c.descripcion());
		assertEquals(3, c.lineaInicio());
	}

	@Test
	void reconoceConstructores() {
		assertEquals("constructor Cuenta() de la clase Cuenta", ExtractorContexto.extraer(FUENTE, 14, 14).descripcion());
	}

	@Test
	void siNoCompilaUsaUnaVentanaDeLineas() {
		Contexto c = ExtractorContexto.extraer("class {\n roto\n", 2, 2);

		assertTrue(c.descripcion().startsWith("fragmento"));
		assertEquals(1, c.lineaInicio());
	}
}
