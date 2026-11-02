package com.tesis.orquestador;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orquestador")
public record OrquestadorProperties(String workspace, Sonar sonar, Docker docker, Llm llm) {

	public record Sonar(String url, String urlDesdeDocker, String token, String perfil) {

		public boolean tieneToken() {
			return token != null && !token.isBlank();
		}
	}

	public record Docker(String imagenScanner, String imagenMaven, String imagenGradle, int timeoutMinutos) {
	}

	/**
	 * @param claudeEjecutable ruta de claude.exe (se llama directamente, no por el .cmd de npm)
	 * @param modelos          modelos que se ofrecen en pantalla; el primero es el predeterminado
	 */
	public record Llm(String claudeEjecutable, java.util.List<String> modelos, int timeoutSegundos,
			String versionPrompt) {
	}
}
