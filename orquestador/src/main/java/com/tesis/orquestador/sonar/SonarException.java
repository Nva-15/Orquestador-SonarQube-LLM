package com.tesis.orquestador.sonar;

public class SonarException extends RuntimeException {

	public SonarException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
