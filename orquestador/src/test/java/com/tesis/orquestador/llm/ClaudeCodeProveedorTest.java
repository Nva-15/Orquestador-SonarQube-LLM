package com.tesis.orquestador.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tesis.orquestador.OrquestadorProperties;

import tools.jackson.databind.json.JsonMapper;

class ClaudeCodeProveedorTest {

	private final ClaudeCodeProveedor proveedor = new ClaudeCodeProveedor(
			new OrquestadorProperties(null, null, null,
					new OrquestadorProperties.Llm("claude.exe", List.of("haiku"), 60, "v1")),
			JsonMapper.builder().build());

	@Test
	void leeLaSalidaEstructuradaYElConsumo() throws LlmException {
		String salida = """
				{"is_error":false,"total_cost_usd":0.0085,
				 "usage":{"input_tokens":3700,"cache_creation_input_tokens":0,"cache_read_input_tokens":100,"output_tokens":973},
				 "modelUsage":{"claude-haiku-4-5-20251001":{}},
				 "structured_output":{"veredicto":"FALSO_POSITIVO"}}""";

		var r = proveedor.interpretar(salida, "", 0, 9000);

		assertEquals("{\"veredicto\":\"FALSO_POSITIVO\"}", r.json());
		assertEquals("claude-haiku-4-5-20251001", r.modeloReal());
		assertEquals(3800, r.tokensEntrada());
		assertEquals(973, r.tokensSalida());
		assertEquals(0.0085, r.costoReferencia());
	}

	@Test
	void detectaElLimiteDeUsoDelPlan() {
		var e = assertThrows(LlmException.class, () -> proveedor.interpretar(
				"{\"is_error\":true,\"result\":\"You've hit your limit · resets 3pm\"}", "", 1, 100));
		assertTrue(e.esLimiteDeUso());
	}

	@Test
	void unErrorCorrienteNoDetieneElLote() {
		var e = assertThrows(LlmException.class, () -> proveedor.interpretar("no es json", "fallo", 1, 100));
		assertFalse(e.esLimiteDeUso());
	}

	@Test
	void enWindowsEscapaLasComillasDelJson() {
		assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));
		assertEquals("\"{\\\"a\\\":\\\"b c\\\"}\"", ClaudeCodeProveedor.argumento("{\"a\":\"b c\"}"));
		assertEquals("\"\"", ClaudeCodeProveedor.argumento(""));
	}
}
