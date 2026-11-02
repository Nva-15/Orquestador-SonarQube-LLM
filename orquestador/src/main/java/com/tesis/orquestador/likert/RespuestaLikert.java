package com.tesis.orquestador.likert;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Valoración de un participante sobre un ítem, en la condición (G1 o G2) que le tocó ver. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "itemId", "participante" }),
		indexes = @Index(columnList = "cuestionarioId"))
public class RespuestaLikert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long cuestionarioId;

	private Long itemId;

	private Long hallazgoId;

	private String participante;

	/** "G1" o "G2". */
	private String condicion;

	private int claridad;

	private int pertinencia;

	private int accionabilidad;

	@Column(length = 4000)
	private String comentario;

	private LocalDateTime fecha;

	protected RespuestaLikert() {
	}

	public RespuestaLikert(ItemCuestionario item, String participante, String condicion) {
		this.cuestionarioId = item.getCuestionarioId();
		this.itemId = item.getId();
		this.hallazgoId = item.getHallazgoId();
		this.participante = participante;
		this.condicion = condicion;
	}

	public void valorar(int claridad, int pertinencia, int accionabilidad, String comentario) {
		for (int v : new int[] { claridad, pertinencia, accionabilidad }) {
			if (v < 1 || v > 5) {
				throw new IllegalArgumentException("Las valoraciones van de 1 a 5");
			}
		}
		this.claridad = claridad;
		this.pertinencia = pertinencia;
		this.accionabilidad = accionabilidad;
		this.comentario = comentario == null || comentario.isBlank() ? null : comentario.strip();
		this.fecha = LocalDateTime.now();
	}

	public double getMedia() {
		return (claridad + pertinencia + accionabilidad) / 3.0;
	}

	public Long getId() {
		return id;
	}

	public Long getCuestionarioId() {
		return cuestionarioId;
	}

	public Long getItemId() {
		return itemId;
	}

	public Long getHallazgoId() {
		return hallazgoId;
	}

	public String getParticipante() {
		return participante;
	}

	public String getCondicion() {
		return condicion;
	}

	public int getClaridad() {
		return claridad;
	}

	public int getPertinencia() {
		return pertinencia;
	}

	public int getAccionabilidad() {
		return accionabilidad;
	}

	public String getComentario() {
		return comentario;
	}

	public LocalDateTime getFecha() {
		return fecha;
	}
}
