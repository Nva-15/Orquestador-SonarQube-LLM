package com.tesis.orquestador.etiquetado;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Registro de la ficha técnica: la condición real de un hallazgo según un evaluador humano. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "hallazgoId", "evaluador" }),
		indexes = @Index(columnList = "analisisId"))
public class Etiqueta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long hallazgoId;

	private Long analisisId;

	private String evaluador;

	@Enumerated(EnumType.STRING)
	private Condicion condicion;

	@Column(length = 4000)
	private String observaciones;

	private LocalDateTime fecha;

	protected Etiqueta() {
	}

	public Etiqueta(Long hallazgoId, Long analisisId, String evaluador) {
		this.hallazgoId = hallazgoId;
		this.analisisId = analisisId;
		this.evaluador = evaluador;
	}

	public void registrar(Condicion condicion, String observaciones) {
		this.condicion = condicion;
		this.observaciones = observaciones == null || observaciones.isBlank() ? null : observaciones.strip();
		this.fecha = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Long getHallazgoId() {
		return hallazgoId;
	}

	public Long getAnalisisId() {
		return analisisId;
	}

	public String getEvaluador() {
		return evaluador;
	}

	public Condicion getCondicion() {
		return condicion;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public LocalDateTime getFecha() {
		return fecha;
	}
}
