package com.tesis.orquestador.etiquetado;

/** Etiqueta humana (verdad de referencia). Mismos nombres que el veredicto del LLM para poder compararlos. */
public enum Condicion {
	VERDADERO_POSITIVO("Problema real (VP)"),
	FALSO_POSITIVO("Falso positivo (FP)");

	private final String etiqueta;

	Condicion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}
}
