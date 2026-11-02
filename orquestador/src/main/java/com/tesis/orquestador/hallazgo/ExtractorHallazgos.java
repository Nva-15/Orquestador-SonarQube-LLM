package com.tesis.orquestador.hallazgo;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.tesis.orquestador.escaneo.EspacioTrabajo;
import com.tesis.orquestador.sonar.SonarClient;

/** Lee los hallazgos de Sonar y les añade la regla y el contexto sintáctico desde la copia de trabajo. */
@Service
public class ExtractorHallazgos {

	private static final int MAX_DESCRIPCION_REGLA = 3_000;

	private final SonarClient sonar;
	private final EspacioTrabajo espacio;
	private final HallazgoRepository repo;

	public ExtractorHallazgos(SonarClient sonar, EspacioTrabajo espacio, HallazgoRepository repo) {
		this.sonar = sonar;
		this.espacio = espacio;
		this.repo = repo;
	}

	/** Sustituye los hallazgos guardados del análisis por los actuales de Sonar. Devuelve cuántos hay. */
	public int extraer(Long analisisId, String projectKey, Consumer<String> log) {
		Path copia = espacio.ruta(projectKey);
		List<Hallazgo> hallazgos = new ArrayList<>();
		for (Map<?, ?> i : sonar.issuesAbiertos(projectKey)) {
			Map<?, ?> rango = (Map<?, ?>) i.get("textRange");
			hallazgos.add(new Hallazgo(analisisId, (String) i.get("key"), (String) i.get("rule"),
					(String) i.get("type"), (String) i.get("severity"), (String) i.get("message"),
					rutaRelativa(i.get("component")), linea(i.get("line"), rango, "startLine"),
					linea(null, rango, "endLine")));
		}
		for (Map<?, ?> h : sonar.hotspotsPendientes(projectKey)) {
			Map<?, ?> rango = (Map<?, ?>) h.get("textRange");
			hallazgos.add(new Hallazgo(analisisId, (String) h.get("key"), (String) h.get("ruleKey"),
					"SECURITY_HOTSPOT", "PROBABILIDAD_" + h.get("vulnerabilityProbability"),
					(String) h.get("message"), rutaRelativa(h.get("component")),
					linea(h.get("line"), rango, "startLine"), linea(null, rango, "endLine")));
		}

		Map<String, SonarClient.Regla> reglas = new HashMap<>();
		Map<String, String> fuentes = new HashMap<>();
		for (Hallazgo h : hallazgos) {
			SonarClient.Regla r = reglas.computeIfAbsent(h.getRegla(), sonar::regla);
			h.describirRegla(r.nombre(), textoPlano(r.descripcionHtml()));
			String fuente = fuentes.computeIfAbsent(h.getArchivo(), a -> leer(copia.resolve(a)));
			if (fuente != null) {
				h.asignarContexto(ExtractorContexto.extraer(fuente, h.getLineaInicio(), h.getLineaFin()));
			}
		}
		repo.deleteByAnalisisId(analisisId);
		repo.saveAll(hallazgos);
		log.accept("Hallazgos extraídos con contexto: " + hallazgos.size() + " (" + reglas.size() + " reglas distintas)");
		return hallazgos.size();
	}

	private static String rutaRelativa(Object componente) {
		String c = (String) componente;
		return c.substring(c.indexOf(':') + 1);
	}

	private static Integer linea(Object linea, Map<?, ?> rango, String campo) {
		if (linea instanceof Number n) {
			return n.intValue();
		}
		return rango != null && rango.get(campo) instanceof Number n ? n.intValue() : null;
	}

	private static String leer(Path archivo) {
		try {
			return Files.readString(archivo, StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			try {
				return Files.readString(archivo, StandardCharsets.ISO_8859_1);
			} catch (IOException ex) {
				return null;
			}
		} catch (IOException e) {
			return null;
		}
	}

	/** Convierte la descripción HTML de la regla en texto para el prompt. */
	static String textoPlano(String html) {
		String texto = html
				.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>|</pre>", "\n")
				.replaceAll("(?i)<li>", "- ")
				.replaceAll("<[^>]+>", "")
				.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
				.replace("&amp;", "&")
				.replaceAll("[ \\t]+\\n", "\n")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
		return texto.length() > MAX_DESCRIPCION_REGLA ? texto.substring(0, MAX_DESCRIPCION_REGLA) + "…" : texto;
	}
}
