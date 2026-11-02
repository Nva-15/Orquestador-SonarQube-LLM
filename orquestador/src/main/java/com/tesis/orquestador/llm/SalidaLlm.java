package com.tesis.orquestador.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Salida del LLM ya validada: nunca se guarda una respuesta que no cumpla el contrato. */
public record SalidaLlm(Veredicto veredicto, double confianza, String justificacion, String explicacion,
		String recomendacion, String codigoCorregido) {

	public static SalidaLlm validar(String json, ObjectMapper mapper) {
		JsonNode n = mapper.readTree(json);
		Veredicto v;
		try {
			v = Veredicto.valueOf(n.path("veredicto").asString(""));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Veredicto no válido: " + n.path("veredicto"));
		}
		if (!n.path("confianza").isNumber()) {
			throw new IllegalArgumentException("Confianza ausente o no numérica");
		}
		double confianza = n.path("confianza").asDouble();
		if (confianza < 0 || confianza > 1) {
			throw new IllegalArgumentException("Confianza fuera de 0–1: " + confianza);
		}
		return new SalidaLlm(v, confianza, requerido(n, "justificacion"), requerido(n, "explicacion"),
				requerido(n, "recomendacion"), n.path("codigoCorregido").asString("").strip());
	}

	private static String requerido(JsonNode n, String campo) {
		String valor = n.path(campo).asString("").strip();
		if (valor.isEmpty()) {
			throw new IllegalArgumentException("Campo vacío: " + campo);
		}
		return valor;
	}
}
