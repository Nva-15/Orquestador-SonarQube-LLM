package com.tesis.orquestador.hallazgo;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Hallazgo de SonarQube (G1) con su contexto sintáctico, tal como se envía al LLM. */
@Entity
@Table(indexes = @Index(columnList = "analisisId"))
public class Hallazgo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long analisisId;

	/** Clave del issue o hotspot en Sonar. */
	private String claveSonar;

	private String regla;

	private String reglaNombre;

	@Lob
	private String reglaDescripcion;

	/** BUG, VULNERABILITY, CODE_SMELL o SECURITY_HOTSPOT. */
	private String tipo;

	private String severidad;

	@Column(length = 2000)
	private String mensaje;

	@Column(length = 1000)
	private String archivo;

	private Integer lineaInicio;

	private Integer lineaFin;

	private boolean esTest;

	/** Por ejemplo: "método save(Owner) de la clase OwnerController". */
	@Column(length = 1000)
	private String contextoDescripcion;

	/** Número de línea de la primera línea de {@link #contexto}. */
	private int contextoInicio;

	@Lob
	private String contexto;

	protected Hallazgo() {
	}

	public Hallazgo(Long analisisId, String claveSonar, String regla, String tipo, String severidad, String mensaje,
			String archivo, Integer lineaInicio, Integer lineaFin) {
		this.analisisId = analisisId;
		this.claveSonar = claveSonar;
		this.regla = regla;
		this.tipo = tipo;
		this.severidad = severidad;
		this.mensaje = mensaje;
		this.archivo = archivo;
		this.lineaInicio = lineaInicio;
		this.lineaFin = lineaFin;
		this.esTest = archivo != null && archivo.contains("src/test/");
	}

	public void describirRegla(String nombre, String descripcion) {
		this.reglaNombre = nombre;
		this.reglaDescripcion = descripcion;
	}

	public void asignarContexto(Contexto c) {
		this.contextoDescripcion = c.descripcion();
		this.contextoInicio = c.lineaInicio();
		this.contexto = c.codigo();
	}

	/** Líneas del contexto numeradas, marcando las que señala Sonar. */
	public List<LineaCodigo> lineas() {
		List<LineaCodigo> lineas = new ArrayList<>();
		if (contexto == null) {
			return lineas;
		}
		int n = contextoInicio;
		for (String texto : contexto.split("\n", -1)) {
			lineas.add(new LineaCodigo(n, texto, marcada(n)));
			n++;
		}
		return lineas;
	}

	private boolean marcada(int n) {
		if (lineaInicio == null) {
			return false;
		}
		int fin = lineaFin != null ? lineaFin : lineaInicio;
		return n >= lineaInicio && n <= fin;
	}

	public record LineaCodigo(int numero, String texto, boolean marcada) {
	}

	public Long getId() {
		return id;
	}

	public Long getAnalisisId() {
		return analisisId;
	}

	public String getClaveSonar() {
		return claveSonar;
	}

	public String getRegla() {
		return regla;
	}

	public String getReglaNombre() {
		return reglaNombre;
	}

	public String getReglaDescripcion() {
		return reglaDescripcion;
	}

	public String getTipo() {
		return tipo;
	}

	public String getSeveridad() {
		return severidad;
	}

	public String getMensaje() {
		return mensaje;
	}

	public String getArchivo() {
		return archivo;
	}

	public Integer getLineaInicio() {
		return lineaInicio;
	}

	public Integer getLineaFin() {
		return lineaFin;
	}

	public boolean isEsTest() {
		return esTest;
	}

	public String getContextoDescripcion() {
		return contextoDescripcion;
	}

	public int getContextoInicio() {
		return contextoInicio;
	}

	public String getContexto() {
		return contexto;
	}
}
