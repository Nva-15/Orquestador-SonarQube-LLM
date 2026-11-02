package com.tesis.orquestador.escaneo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.tesis.orquestador.OrquestadorProperties;

/**
 * Compila y escanea la copia de trabajo dentro de contenedores Docker: no hace falta Maven, Gradle ni el
 * scanner instalados, y el código de build de un repositorio ajeno no se ejecuta en el equipo anfitrión.
 */
@Component
public class EscanerDocker {

	private static final String SIN_CLASES = ".orquestador-sin-clases";

	private final OrquestadorProperties.Docker docker;
	private final OrquestadorProperties.Sonar sonar;
	private final Duration limite;

	public EscanerDocker(OrquestadorProperties props) {
		this.docker = props.docker();
		this.sonar = props.sonar();
		this.limite = Duration.ofMinutes(docker.timeoutMinutos());
	}

	/** Intenta compilar; si falla, el análisis sigue sin clases (Sonar avisa de que es menos preciso). */
	public boolean compilar(Path repo, TipoBuild tipo, Consumer<String> log) throws IOException, InterruptedException {
		List<String> comando = comandoCompilacion(repo, tipo);
		if (comando.isEmpty()) {
			log.accept("Sin pom.xml ni build.gradle: se escanea sin compilar");
			return false;
		}
		log.accept("Compilando con " + tipo.etiqueta() + " (Docker)");
		int codigo = ProcesoExterno.ejecutar(comando, Map.of(), repo, limite, log);
		if (codigo != 0) {
			log.accept("AVISO: la compilación terminó con código " + codigo + "; se escanea sin clases compiladas");
			return false;
		}
		return true;
	}

	List<String> comandoCompilacion(Path repo, TipoBuild tipo) {
		return switch (tipo) {
			case MAVEN -> List.of("docker", "run", "--rm", "-v", volumen(repo, "/src"), "-v", "orquestador-m2:/root/.m2",
					"-w", "/src", docker.imagenMaven(), "mvn", "-B", "-q", "-ntp", "-DskipTests", "test-compile",
					// Las dependencias permiten a Sonar resolver tipos externos (menos falsos positivos)
					"dependency:copy-dependencies", "-DoutputDirectory=" + tipo.librerias());
			case GRADLE -> List.of("docker", "run", "--rm", "-v", volumen(repo, "/src"), "-v",
					"orquestador-gradle:/home/gradle/.gradle", "-w", "/src", docker.imagenGradle(), "sh", "-c",
					"if [ -f gradlew ]; then sh ./gradlew --no-daemon -q compileJava compileTestJava; "
							+ "else gradle --no-daemon -q compileJava compileTestJava; fi");
			case SIN_COMPILAR -> List.of();
		};
	}

	/** Ejecuta el scanner y devuelve el ceTaskId que Sonar procesará en segundo plano. */
	public String escanear(Path repo, String projectKey, TipoBuild tipo, boolean compilado, Consumer<String> log)
			throws IOException, InterruptedException {
		List<String> comando = comandoScanner(repo, projectKey, tipo, compilado);
		log.accept("Ejecutando sonar-scanner (Docker)");
		int codigo = ProcesoExterno.ejecutar(comando, Map.of("SONAR_TOKEN", sonar.token()), repo, limite, log);
		if (codigo != 0) {
			throw new IOException("sonar-scanner terminó con código " + codigo);
		}
		Path reporte = repo.resolve(".scannerwork/report-task.txt");
		var props = new Properties();
		try (var in = Files.newBufferedReader(reporte)) {
			props.load(in);
		}
		return props.getProperty("ceTaskId");
	}

	List<String> comandoScanner(Path repo, String projectKey, TipoBuild tipo, boolean compilado) throws IOException {
		List<String> c = new ArrayList<>(List.of("docker", "run", "--rm",
				"-e", "SONAR_HOST_URL=" + sonar.urlDesdeDocker(),
				// Se hereda del entorno del proceso: el token no aparece en la línea de comandos
				"-e", "SONAR_TOKEN",
				"-v", volumen(repo, "/usr/src"),
				"-v", "orquestador-scanner-cache:/opt/sonar-scanner/.sonar/cache",
				docker.imagenScanner(),
				"-Dsonar.projectKey=" + projectKey,
				"-Dsonar.projectName=" + projectKey,
				"-Dsonar.sources=.",
				"-Dsonar.tests=.",
				// Solo Java (alcance de la tesis); los tests se analizan aparte de producción
				"-Dsonar.inclusions=**/*.java",
				"-Dsonar.exclusions=**/src/test/**,**/target/**,**/build/**",
				"-Dsonar.test.inclusions=**/src/test/**/*.java",
				"-Dsonar.test.exclusions=**/target/**,**/build/**",
				"-Dsonar.scm.disabled=true",
				// La imagen usa /tmp por defecto: así report-task.txt queda en la copia de trabajo
				"-Dsonar.working.directory=/usr/src/.scannerwork"));
		List<String> clases = compilado ? carpetas(repo, tipo.clases()) : List.of();
		List<String> clasesTest = compilado ? carpetas(repo, tipo.clasesTest()) : List.of();
		if (clases.isEmpty()) {
			Files.createDirectories(repo.resolve(SIN_CLASES));
			clases = List.of(SIN_CLASES);
		}
		c.add("-Dsonar.java.binaries=" + String.join(",", clases));
		if (!clasesTest.isEmpty()) {
			c.add("-Dsonar.java.test.binaries=" + String.join(",", clasesTest));
		}
		List<String> librerias = compilado ? carpetas(repo, tipo.librerias()) : List.of();
		if (!librerias.isEmpty()) {
			String jars = String.join(",", librerias.stream().map(d -> d + "/*.jar").toList());
			c.add("-Dsonar.java.libraries=" + jars);
			c.add("-Dsonar.java.test.libraries=" + jars);
		}
		return c;
	}

	/** Carpetas de clases existentes (relativas a la raíz), incluidas las de submódulos. */
	private static List<String> carpetas(Path repo, String sufijo) throws IOException {
		if (sufijo == null) {
			return List.of();
		}
		Path sufijoPath = Path.of(sufijo);
		try (Stream<Path> rutas = Files.walk(repo, 8)) {
			return rutas.filter(Files::isDirectory)
					.filter(p -> p.endsWith(sufijoPath))
					.map(p -> repo.relativize(p).toString().replace('\\', '/'))
					.sorted()
					.toList();
		}
	}

	private static String volumen(Path repo, String destino) {
		return repo.toAbsolutePath() + ":" + destino;
	}
}
