package com.tesis.orquestador.llm;

public enum Veredicto {
	VERDADERO_POSITIVO("Problema real"),
	FALSO_POSITIVO("Falso positivo"),
	REQUIERE_REVISION_HUMANA("Revisión humana");

	private final String etiqueta;

	Veredicto(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}
}
