package com.tesis.orquestador.analisis;

public enum EstadoAnalisis {
	PENDIENTE, PREPARANDO, COMPILANDO, ESCANEANDO, PROCESANDO_EN_SONAR, COMPLETADO, ERROR;

	public boolean enCurso() {
		return this != COMPLETADO && this != ERROR;
	}
}
