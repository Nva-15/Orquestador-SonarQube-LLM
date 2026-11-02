package com.tesis.orquestador.likert;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Hallazgo del cuestionario con los textos de ambas condiciones congelados al crearlo, para que cambios
 * posteriores (p. ej. repetir una corrida) no alteren lo que ya se valoró.
 */
@Entity
@Table(indexes = @Index(columnList = "cuestionarioId"))
public class ItemCuestionario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long cuestionarioId;

	private Long hallazgoId;

	/** Recomendación del LLM usada como texto G2 (trazabilidad). */
	private Long recomendacionId;

	/** G1: lo que ofrece SonarQube (descripción de la regla). */
	@Lob
	private String textoG1;

	/** G2: explicación y recomendación del LLM. */
	@Lob
	private String textoG2;

	/** G2: código corregido propuesto (puede estar vacío). */
	@Lob
	private String codigoG2;

	protected ItemCuestionario() {
	}

	public ItemCuestionario(Long cuestionarioId, Long hallazgoId, Long recomendacionId, String textoG1, String textoG2,
			String codigoG2) {
		this.cuestionarioId = cuestionarioId;
		this.hallazgoId = hallazgoId;
		this.recomendacionId = recomendacionId;
		this.textoG1 = textoG1;
		this.textoG2 = textoG2;
		this.codigoG2 = codigoG2;
	}

	public Long getId() {
		return id;
	}

	public Long getCuestionarioId() {
		return cuestionarioId;
	}

	public Long getHallazgoId() {
		return hallazgoId;
	}

	public Long getRecomendacionId() {
		return recomendacionId;
	}

	public String getTextoG1() {
		return textoG1;
	}

	public String getTextoG2() {
		return textoG2;
	}

	public String getCodigoG2() {
		return codigoG2;
	}
}
