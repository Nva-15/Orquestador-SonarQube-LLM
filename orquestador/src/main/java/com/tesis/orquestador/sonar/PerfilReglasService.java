package com.tesis.orquestador.sonar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.tesis.orquestador.OrquestadorProperties;

/** Garantiza que exista en Sonar el perfil "Tesis Java" con las reglas de reglas-tesis.txt (OE1). */
@Service
public class PerfilReglasService {

	private final SonarClient sonar;
	private final String nombrePerfil;
	private volatile boolean sincronizado;

	public PerfilReglasService(SonarClient sonar, OrquestadorProperties props) {
		this.sonar = sonar;
		this.nombrePerfil = props.sonar().perfil();
	}

	public String nombrePerfil() {
		return nombrePerfil;
	}

	/** Crea el perfil si falta y activa las reglas de la lista (idempotente; una vez por arranque). */
	public synchronized void asegurarPerfil(Consumer<String> log) {
		if (sincronizado) {
			return;
		}
		String perfilKey = sonar.buscarPerfilJava(nombrePerfil).orElseGet(() -> {
			log.accept("Creando perfil de calidad '" + nombrePerfil + "' en Sonar");
			return sonar.crearPerfilJava(nombrePerfil);
		});
		List<String> reglas = reglasTesis();
		List<String> fallidas = new ArrayList<>();
		for (String regla : reglas) {
			try {
				sonar.activarRegla(perfilKey, regla);
			} catch (SonarException e) {
				fallidas.add(regla);
			}
		}
		log.accept("Perfil '" + nombrePerfil + "': " + (reglas.size() - fallidas.size()) + " de " + reglas.size()
				+ " reglas activas" + (fallidas.isEmpty() ? "" : "; no existen en esta versión de Sonar: " + fallidas));
		sincronizado = true;
	}

	static List<String> reglasTesis() {
		try {
			String texto = new ClassPathResource("reglas-tesis.txt").getContentAsString(StandardCharsets.UTF_8);
			return texto.lines()
					.map(l -> l.replaceFirst("#.*", "").trim())
					.filter(l -> !l.isEmpty())
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
