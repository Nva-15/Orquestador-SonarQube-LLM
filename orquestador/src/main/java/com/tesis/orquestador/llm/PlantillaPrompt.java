package com.tesis.orquestador.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.tesis.orquestador.OrquestadorProperties;
import com.tesis.orquestador.hallazgo.Hallazgo;

/** Prompt versionado (src/main/resources/prompts/&lt;version&gt;): sistema, plantilla de usuario y JSON Schema. */
@Component
public class PlantillaPrompt {

	private final String version;
	private final String sistema;
	private final String usuario;
	private final String esquema;

	public PlantillaPrompt(OrquestadorProperties props) {
		this.version = props.llm().versionPrompt();
		this.sistema = leer("sistema.txt");
		this.usuario = leer("usuario.txt");
		// En una sola línea: se pasa como argumento a la CLI
		this.esquema = leer("esquema.json").replaceAll("\\s*\\n\\s*", "");
	}

	public String version() {
		return version;
	}

	public String sistema() {
		return sistema;
	}

	public String esquema() {
		return esquema;
	}

	public String usuario(Hallazgo h) {
		Map<String, String> valores = Map.of(
				"regla", h.getRegla(),
				"reglaNombre", texto(h.getReglaNombre()),
				"tipo", texto(h.getTipo()),
				"severidad", texto(h.getSeveridad()),
				"mensaje", texto(h.getMensaje()),
				"archivo", texto(h.getArchivo()),
				"esTest", h.isEsTest() ? " (código de test)" : "",
				"lineas", h.getLineaInicio() == null ? "todo el archivo"
						: h.getLineaInicio() + (h.getLineaFin() != null && !h.getLineaFin().equals(h.getLineaInicio())
								? "–" + h.getLineaFin() : ""),
				"reglaDescripcion", texto(h.getReglaDescripcion()),
				"contextoDescripcion", texto(h.getContextoDescripcion()));
		String prompt = usuario;
		for (var e : valores.entrySet()) {
			prompt = prompt.replace("{{" + e.getKey() + "}}", e.getValue());
		}
		// El código va al final: si contuviera "{{...}}" no se interpretaría como marcador
		return prompt.replace("{{codigo}}", codigoNumerado(h));
	}

	static String codigoNumerado(Hallazgo h) {
		return h.lineas().stream()
				.map(l -> (l.marcada() ? ">> " : "   ") + l.numero() + " | " + l.texto())
				.collect(Collectors.joining("\n"));
	}

	private static String texto(String s) {
		return s == null ? "(no disponible)" : s;
	}

	private String leer(String archivo) {
		try {
			return new ClassPathResource("prompts/" + version + "/" + archivo).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("No existe el prompt " + version + "/" + archivo, e);
		}
	}
}
