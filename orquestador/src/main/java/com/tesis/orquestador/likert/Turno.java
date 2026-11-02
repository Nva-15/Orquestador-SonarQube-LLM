package com.tesis.orquestador.likert;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Orden de llegada de un participante a un cuestionario. Los turnos pares ven la mitad A en G1 y la B en G2; los
 * impares, al revés (contrabalanceo).
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "cuestionarioId", "participante" }))
public class Turno {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long cuestionarioId;

	private String participante;

	private int numero;

	protected Turno() {
	}

	public Turno(Long cuestionarioId, String participante, int numero) {
		this.cuestionarioId = cuestionarioId;
		this.participante = participante;
		this.numero = numero;
	}

	public int getNumero() {
		return numero;
	}
}
