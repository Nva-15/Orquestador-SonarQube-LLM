package com.tesis.orquestador.llm;

public class LlmException extends Exception {

	/** true si el fallo se debe al límite de uso del plan: no tiene sentido seguir con el lote. */
	private final boolean limiteDeUso;

	public LlmException(String mensaje, boolean limiteDeUso) {
		super(mensaje);
		this.limiteDeUso = limiteDeUso;
	}

	public LlmException(String mensaje, Throwable causa) {
		super(mensaje, causa);
		this.limiteDeUso = false;
	}

	public boolean esLimiteDeUso() {
		return limiteDeUso;
	}
}
