package com.tesis.orquestador.llm;

/**
 * Punto de extensión del orquestador (OE2): cualquier LLM que reciba un prompt de sistema, un prompt de usuario
 * y un JSON Schema, y devuelva un JSON que lo cumpla. Implementaciones previstas: Claude Code (suscripción),
 * API de Anthropic y Ollama.
 */
public interface ProveedorLlm {

	/** Identificador corto que se guarda en cada recomendación (p. ej. "claude-code"). */
	String nombre();

	RespuestaLlm generar(PeticionLlm peticion) throws LlmException;

	record PeticionLlm(String modelo, String promptSistema, String promptUsuario, String esquemaJson) {
	}

	/**
	 * @param json            salida estructurada (JSON que cumple el esquema)
	 * @param modeloReal      identificador exacto del modelo que respondió
	 * @param costoReferencia coste en USD según precios de lista (con suscripción no se cobra)
	 */
	record RespuestaLlm(String json, String modeloReal, Integer tokensEntrada, Integer tokensSalida,
			Double costoReferencia, long latenciaMs) {
	}
}
