package com.tesis.orquestador.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tesis.orquestador.OrquestadorProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM a través de Claude Code en modo no interactivo, con la suscripción del usuario (sin API key): sin
 * herramientas, sin sesión guardada, con prompt de sistema propio y salida validada contra el JSON Schema.
 */
@Component
public class ClaudeCodeProveedor implements ProveedorLlm {

	private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
	private static final Pattern LIMITE = Pattern.compile("(?i)usage limit|rate limit|limit reached|hit your limit");

	private final OrquestadorProperties.Llm config;
	private final ObjectMapper json;

	public ClaudeCodeProveedor(OrquestadorProperties props, ObjectMapper json) {
		this.config = props.llm();
		this.json = json;
	}

	@Override
	public String nombre() {
		return "claude-code";
	}

	@Override
	public RespuestaLlm generar(PeticionLlm p) throws LlmException {
		Path sistema = null;
		try {
			sistema = Files.createTempFile("orquestador-sistema", ".txt");
			Files.writeString(sistema, p.promptSistema(), StandardCharsets.UTF_8);
			var pb = new ProcessBuilder(comando(p, sistema));
			// Sin ANTHROPIC_API_KEY Claude Code usa la sesión de la suscripción, que no genera cobros por uso
			pb.environment().remove("ANTHROPIC_API_KEY");
			long inicio = System.nanoTime();
			Process proceso = pb.start();
			CompletableFuture<String> salida = CompletableFuture.supplyAsync(() -> leer(proceso.getInputStream()));
			CompletableFuture<String> errores = CompletableFuture.supplyAsync(() -> leer(proceso.getErrorStream()));
			try (var in = proceso.getOutputStream()) {
				in.write(p.promptUsuario().getBytes(StandardCharsets.UTF_8));
			}
			if (!proceso.waitFor(config.timeoutSegundos(), TimeUnit.SECONDS)) {
				proceso.destroyForcibly();
				throw new LlmException("Claude Code no respondió en " + config.timeoutSegundos() + " s", false);
			}
			long latencia = (System.nanoTime() - inicio) / 1_000_000;
			return interpretar(salida.join(), errores.join(), proceso.exitValue(), latencia);
		} catch (IOException e) {
			throw new LlmException("No se pudo ejecutar Claude Code (" + config.claudeEjecutable() + "): "
					+ e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LlmException("Interrumpido", e);
		} finally {
			if (sistema != null) {
				sistema.toFile().delete();
			}
		}
	}

	List<String> comando(PeticionLlm p, Path sistema) {
		List<String> c = new ArrayList<>(List.of(config.claudeEjecutable(), "-p",
				"--model", p.modelo(),
				"--tools", argumento(""),
				"--no-session-persistence",
				"--output-format", "json",
				"--json-schema", argumento(p.esquemaJson()),
				"--system-prompt-file", sistema.toString()));
		return c;
	}

	RespuestaLlm interpretar(String salida, String errores, int codigo, long latenciaMs) throws LlmException {
		JsonNode r;
		try {
			r = json.readTree(salida);
		} catch (RuntimeException e) {
			String detalle = (salida + " " + errores).strip();
			throw new LlmException("Respuesta no válida de Claude Code (código " + codigo + "): "
					+ recortar(detalle), LIMITE.matcher(detalle).find());
		}
		JsonNode estructurada = r.path("structured_output");
		if (codigo != 0 || r.path("is_error").asBoolean(false) || estructurada.isMissingNode()
				|| estructurada.isNull()) {
			String detalle = r.path("result").asString("") + " " + errores;
			throw new LlmException("Claude Code devolvió un error: " + recortar(detalle.strip()),
					LIMITE.matcher(detalle).find());
		}
		JsonNode uso = r.path("usage");
		int entrada = uso.path("input_tokens").asInt(0) + uso.path("cache_creation_input_tokens").asInt(0)
				+ uso.path("cache_read_input_tokens").asInt(0);
		String modeloReal = r.path("modelUsage").propertyNames().stream().findFirst().orElse(null);
		return new RespuestaLlm(estructurada.toString(), modeloReal, entrada, uso.path("output_tokens").asInt(0),
				r.path("total_cost_usd").asDouble(0), latenciaMs);
	}

	/**
	 * En Windows, Java no escapa las comillas internas de los argumentos: se citan a mano con las reglas de
	 * CommandLineToArgvW para que el JSON llegue intacto.
	 */
	static String argumento(String valor) {
		if (!WINDOWS) {
			return valor;
		}
		StringBuilder b = new StringBuilder("\"");
		int barras = 0;
		for (char ch : valor.toCharArray()) {
			if (ch == '\\') {
				barras++;
				continue;
			}
			if (ch == '"') {
				b.append("\\".repeat(barras * 2 + 1)).append('"');
			} else {
				b.append("\\".repeat(barras)).append(ch);
			}
			barras = 0;
		}
		return b.append("\\".repeat(barras * 2)).append('"').toString();
	}

	private static String leer(java.io.InputStream in) {
		try {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	private static String recortar(String s) {
		return s.length() > 500 ? s.substring(0, 500) + "…" : s;
	}
}
