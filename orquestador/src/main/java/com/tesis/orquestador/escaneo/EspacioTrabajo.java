package com.tesis.orquestador.escaneo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.tesis.orquestador.OrquestadorProperties;

/**
 * Prepara la copia de trabajo de un repositorio en la carpeta "repositorios/". El original nunca se
 * compila ni se modifica: una ruta local se copia y una URL git se clona.
 */
@Component
public class EspacioTrabajo {

	/** Carpetas que no se copian: historial, salidas de build y dependencias. */
	private static final Set<String> IGNORADAS = Set.of(".git", "target", "build", ".gradle", "node_modules",
			".scannerwork", ".idea", ".vscode");

	private final Path raiz;
	private final Duration limite;

	public EspacioTrabajo(OrquestadorProperties props) {
		this.raiz = Path.of(props.workspace()).toAbsolutePath().normalize();
		this.limite = Duration.ofMinutes(props.docker().timeoutMinutos());
	}

	public static boolean esUrlGit(String origen) {
		return origen.startsWith("https://") || origen.startsWith("http://") || origen.startsWith("git@");
	}

	/** Clave de proyecto Sonar válida a partir del nombre de la carpeta o del repositorio. */
	public static String claveDesde(String origen) {
		String limpio = origen.trim().replaceAll("[/\\\\]+$", "").replaceAll("\\.git$", "");
		String nombre = limpio.substring(Math.max(limpio.lastIndexOf('/'), limpio.lastIndexOf('\\')) + 1);
		String clave = nombre.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
		return clave.isEmpty() || clave.matches("\\d+") ? "repo-" + clave : clave;
	}

	/** Carpeta de la copia de trabajo de un proyecto. */
	public Path ruta(String projectKey) {
		return raiz.resolve(projectKey).normalize();
	}

	public Path preparar(String origen, String projectKey, Consumer<String> log)
			throws IOException, InterruptedException {
		Path destino = raiz.resolve(projectKey).normalize();
		if (!destino.getParent().equals(raiz)) {
			throw new IllegalArgumentException("Clave de proyecto no válida: " + projectKey);
		}
		borrar(destino);
		Files.createDirectories(raiz);
		if (esUrlGit(origen)) {
			log.accept("Clonando " + origen);
			int codigo = ProcesoExterno.ejecutar(List.of("git", "clone", "--depth", "1", origen, destino.toString()),
					Map.of(), raiz, limite, log);
			if (codigo != 0) {
				throw new IOException("git clone terminó con código " + codigo);
			}
		} else {
			Path fuente = Path.of(origen).toAbsolutePath().normalize();
			if (!Files.isDirectory(fuente)) {
				throw new IOException("La ruta no existe o no es una carpeta: " + fuente);
			}
			log.accept("Copiando " + fuente + " a " + destino);
			copiar(fuente, destino);
		}
		long javas = contarJava(destino);
		if (javas == 0) {
			throw new IOException("El repositorio no contiene archivos .java");
		}
		log.accept("Copia de trabajo lista: " + javas + " archivos .java");
		return destino;
	}

	private static void copiar(Path fuente, Path destino) throws IOException {
		Files.walkFileTree(fuente, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (!dir.equals(fuente) && IGNORADAS.contains(dir.getFileName().toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(destino.resolve(fuente.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, destino.resolve(fuente.relativize(file).toString()),
						StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** Borra una copia de trabajo anterior; solo actúa dentro de la carpeta "repositorios/". */
	private void borrar(Path dir) throws IOException {
		if (!Files.exists(dir) || !dir.startsWith(raiz) || dir.equals(raiz)) {
			return;
		}
		try (Stream<Path> rutas = Files.walk(dir)) {
			for (Path p : rutas.sorted(Comparator.reverseOrder()).toList()) {
				p.toFile().setWritable(true);
				Files.delete(p);
			}
		}
	}

	private static long contarJava(Path dir) throws IOException {
		try (Stream<Path> rutas = Files.walk(dir)) {
			return rutas.filter(p -> p.toString().endsWith(".java")).count();
		}
	}
}
