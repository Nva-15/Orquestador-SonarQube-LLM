package com.tesis.orquestador.resultados;

import com.tesis.orquestador.llm.Recomendacion;

/** Condición G2 concreta: un modelo con una versión de prompt (p. ej. haiku · v1). */
public record Corrida(String modelo, String versionPrompt) implements Comparable<Corrida> {

	public static Corrida de(Recomendacion r) {
		return new Corrida(r.getModelo(), r.getVersionPrompt());
	}

	/** Valor para la URL (?corrida=haiku_v1). */
	public String getClave() {
		return modelo + "_" + versionPrompt;
	}

	public String getEtiqueta() {
		return modelo + " · prompt " + versionPrompt;
	}

	/**
	 * Para cada corrida, la respuesta válida más reciente de cada hallazgo.
	 *
	 * @param recomendaciones ordenadas por fecha descendente
	 */
	public static java.util.Map<Corrida, java.util.Map<Long, Recomendacion>> ultimasPorCorrida(
			java.util.List<Recomendacion> recomendaciones) {
		java.util.Map<Corrida, java.util.Map<Long, Recomendacion>> resultado = new java.util.TreeMap<>();
		recomendaciones.stream()
				.filter(Recomendacion::isExitosa)
				.forEach(r -> resultado.computeIfAbsent(de(r), c -> new java.util.HashMap<>())
						.putIfAbsent(r.getHallazgoId(), r));
		return resultado;
	}

	@Override
	public int compareTo(Corrida o) {
		int c = modelo.compareTo(o.modelo);
		return c != 0 ? c : versionPrompt.compareTo(o.versionPrompt);
	}
}
