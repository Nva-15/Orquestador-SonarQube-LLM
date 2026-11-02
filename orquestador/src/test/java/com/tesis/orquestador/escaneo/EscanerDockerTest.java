package com.tesis.orquestador.escaneo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tesis.orquestador.OrquestadorProperties;

class EscanerDockerTest {

	private final EscanerDocker escaner = new EscanerDocker(new OrquestadorProperties("../repositorios",
			new OrquestadorProperties.Sonar("http://localhost:9000", "http://host.docker.internal:9000", "squ_secreto",
					"Tesis Java"),
			new OrquestadorProperties.Docker("scanner", "maven", "gradle", 30), null));

	@TempDir
	Path repo;

	@Test
	void usaLasClasesDeTodosLosModulosMaven() throws IOException {
		Files.createDirectories(repo.resolve("core/target/classes"));
		Files.createDirectories(repo.resolve("web/target/classes"));
		Files.createDirectories(repo.resolve("web/target/test-classes"));

		List<String> c = escaner.comandoScanner(repo, "demo", TipoBuild.MAVEN, true);

		assertTrue(c.contains("-Dsonar.java.binaries=core/target/classes,web/target/classes"));
		assertTrue(c.contains("-Dsonar.java.test.binaries=web/target/test-classes"));
	}

	@Test
	void pasaLasDependenciasMavenYDejaElReporteEnLaCopia() throws IOException {
		Files.createDirectories(repo.resolve("target/orquestador-libs"));

		List<String> c = escaner.comandoScanner(repo, "demo", TipoBuild.MAVEN, true);

		assertTrue(c.contains("-Dsonar.java.libraries=target/orquestador-libs/*.jar"));
		assertTrue(c.contains("-Dsonar.working.directory=/usr/src/.scannerwork"));
	}

	@Test
	void sinCompilarUsaCarpetaVacia() throws IOException {
		List<String> c = escaner.comandoScanner(repo, "demo", TipoBuild.SIN_COMPILAR, false);

		assertTrue(c.contains("-Dsonar.java.binaries=.orquestador-sin-clases"));
		assertTrue(Files.isDirectory(repo.resolve(".orquestador-sin-clases")));
	}

	@Test
	void elTokenNoApareceEnLaLineaDeComandos() throws IOException {
		List<String> c = escaner.comandoScanner(repo, "demo", TipoBuild.MAVEN, false);

		assertFalse(String.join(" ", c).contains("squ_secreto"));
	}
}
