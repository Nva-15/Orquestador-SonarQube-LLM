package com.tesis.orquestador.likert;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Cuestionario Likert sobre una muestra de hallazgos de un análisis, comparando G1 con una corrida G2. */
@Entity
public class Cuestionario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long analisisId;

	/** Corrida G2 cuyas recomendaciones se evalúan (modelo + versión de prompt). */
	private String modelo;

	private String versionPrompt;

	private long semilla;

	private LocalDateTime creado = LocalDateTime.now();

	protected Cuestionario() {
	}

	public Cuestionario(Long analisisId, String modelo, String versionPrompt, long semilla) {
		this.analisisId = analisisId;
		this.modelo = modelo;
		this.versionPrompt = versionPrompt;
		this.semilla = semilla;
	}

	public String getEtiquetaCorrida() {
		return modelo + " · prompt " + versionPrompt;
	}

	public Long getId() {
		return id;
	}

	public Long getAnalisisId() {
		return analisisId;
	}

	public String getModelo() {
		return modelo;
	}

	public String getVersionPrompt() {
		return versionPrompt;
	}

	public long getSemilla() {
		return semilla;
	}

	public LocalDateTime getCreado() {
		return creado;
	}
}
