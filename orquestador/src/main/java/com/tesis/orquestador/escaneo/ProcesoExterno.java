package com.tesis.orquestador.escaneo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Ejecuta un comando, vuelca su salida al log y lo corta si supera el tiempo límite. */
public final class ProcesoExterno {

	private ProcesoExterno() {
	}

	public static int ejecutar(List<String> comando, Map<String, String> entorno, Path directorio,
			Duration limite, Consumer<String> log) throws IOException, InterruptedException {
		var pb = new ProcessBuilder(comando).redirectErrorStream(true);
		if (directorio != null) {
			pb.directory(directorio.toFile());
		}
		pb.environment().putAll(entorno);
		Process proceso = pb.start();
		Thread lector = Thread.ofVirtual().start(() -> {
			try (var in = new BufferedReader(new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
				in.lines().forEach(log);
			} catch (IOException e) {
				log.accept("[no se pudo leer la salida: " + e.getMessage() + "]");
			}
		});
		if (!proceso.waitFor(limite.toMinutes(), TimeUnit.MINUTES)) {
			proceso.descendants().forEach(ProcessHandle::destroyForcibly);
			proceso.destroyForcibly();
			lector.join(5_000);
			throw new IOException("Tiempo límite superado (" + limite.toMinutes() + " min): " + comando.getFirst());
		}
		lector.join();
		return proceso.exitValue();
	}
}
