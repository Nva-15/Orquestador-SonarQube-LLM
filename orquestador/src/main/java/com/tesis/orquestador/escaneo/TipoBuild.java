package com.tesis.orquestador.escaneo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Sistema de build detectado y dónde deja las clases compiladas (las necesita el analizador Java de Sonar). */
public enum TipoBuild {
	MAVEN("maven", "target/classes", "target/test-classes", "target/orquestador-libs"),
	GRADLE("gradle", "build/classes/java/main", "build/classes/java/test", null),
	SIN_COMPILAR("sin-compilar", null, null, null);

	private final String etiqueta;
	private final String clases;
	private final String clasesTest;
	private final String librerias;

	TipoBuild(String etiqueta, String clases, String clasesTest, String librerias) {
		this.etiqueta = etiqueta;
		this.clases = clases;
		this.clasesTest = clasesTest;
		this.librerias = librerias;
	}

	public static TipoBuild detectar(Path raiz) {
		if (Files.exists(raiz.resolve("pom.xml"))) {
			return MAVEN;
		}
		if (Files.exists(raiz.resolve("build.gradle")) || Files.exists(raiz.resolve("build.gradle.kts"))) {
			return GRADLE;
		}
		return SIN_COMPILAR;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Sufijo de las carpetas de clases (válido también en multimódulo). */
	public String clases() {
		return clases;
	}

	public String clasesTest() {
		return clasesTest;
	}

	/** Carpeta donde se copian los .jar de dependencias (solo Maven; en Gradle Sonar analiza sin ellas). */
	public String librerias() {
		return librerias;
	}
}
