package com.tesis.orquestador.analisis;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

/** Una ejecución de SonarQube sobre un repositorio (condición G1 de la tesis). */
@Entity
public class Analisis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Ruta local o URL git indicada por el usuario. */
	@Column(nullable = false, length = 1000)
	private String origen;

	@Column(nullable = false)
	private String projectKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoAnalisis estado = EstadoAnalisis.PENDIENTE;

	/** maven, gradle o sin-compilar. */
	private String tipoBuild;

	private String perfilReglas;

	private String ceTaskId;

	private Integer totalHallazgos;

	private LocalDateTime creado = LocalDateTime.now();

	private LocalDateTime finalizado;

	@Lob
	private String log = "";

	protected Analisis() {
	}

	public Analisis(String origen, String projectKey) {
		this.origen = origen;
		this.projectKey = projectKey;
	}

	public void registrar(String linea) {
		log = log + linea + "\n";
	}

	public void finalizar(EstadoAnalisis estadoFinal) {
		estado = estadoFinal;
		finalizado = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getOrigen() {
		return origen;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public EstadoAnalisis getEstado() {
		return estado;
	}

	public void setEstado(EstadoAnalisis estado) {
		this.estado = estado;
	}

	public String getTipoBuild() {
		return tipoBuild;
	}

	public void setTipoBuild(String tipoBuild) {
		this.tipoBuild = tipoBuild;
	}

	public String getPerfilReglas() {
		return perfilReglas;
	}

	public void setPerfilReglas(String perfilReglas) {
		this.perfilReglas = perfilReglas;
	}

	public String getCeTaskId() {
		return ceTaskId;
	}

	public void setCeTaskId(String ceTaskId) {
		this.ceTaskId = ceTaskId;
	}

	public Integer getTotalHallazgos() {
		return totalHallazgos;
	}

	public void setTotalHallazgos(Integer totalHallazgos) {
		this.totalHallazgos = totalHallazgos;
	}

	public LocalDateTime getCreado() {
		return creado;
	}

	public LocalDateTime getFinalizado() {
		return finalizado;
	}

	public String getLog() {
		return log;
	}
}
