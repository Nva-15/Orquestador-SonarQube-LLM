package com.tesis.orquestador.likert;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Desarrollador que responde el cuestionario (perfil para describir la muestra). */
@Entity
public class Participante {

	@Id
	private String codigo;

	/** Rango de años de experiencia con Java. */
	private String experiencia;

	protected Participante() {
	}

	public Participante(String codigo, String experiencia) {
		this.codigo = codigo;
		this.experiencia = experiencia;
	}

	public void setExperiencia(String experiencia) {
		this.experiencia = experiencia;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getExperiencia() {
		return experiencia;
	}
}
