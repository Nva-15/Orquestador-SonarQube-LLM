package com.tesis.orquestador.llm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Una llamada al LLM para un hallazgo (condición G2), con todo lo necesario para reproducirla: proveedor,
 * modelo, versión y texto del prompt, tokens, coste de referencia y latencia. Si falló, solo hay {@link #error}.
 */
@Entity
@Table(indexes = @Index(columnList = "hallazgoId"))
public class Recomendacion {

	/** "1) ", "2. " al inicio del texto o tras un espacio. */
	private static final Pattern NUMERO_PASO = Pattern.compile("(?:^|(?<=\\s))(\\d{1,2})[).]\\s+");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long hallazgoId;

	private String proveedor;

	/** Modelo pedido (alias, p. ej. "haiku"). */
	private String modelo;

	/** Modelo exacto que respondió (p. ej. "claude-haiku-4-5-20251001"). */
	private String modeloReal;

	private String versionPrompt;

	@Lob
	private String prompt;

	@Enumerated(EnumType.STRING)
	private Veredicto veredicto;

	private Double confianza;

	@Lob
	private String justificacion;

	@Lob
	private String explicacion;

	@Lob
	private String recomendacion;

	@Lob
	private String codigoCorregido;

	private Integer tokensEntrada;

	private Integer tokensSalida;

	private Double costoReferenciaUsd;

	private Long latenciaMs;

	@Lob
	private String error;

	private LocalDateTime fecha = LocalDateTime.now();

	protected Recomendacion() {
	}

	public Recomendacion(Long hallazgoId, String proveedor, String modelo, String versionPrompt, String prompt) {
		this.hallazgoId = hallazgoId;
		this.proveedor = proveedor;
		this.modelo = modelo;
		this.versionPrompt = versionPrompt;
		this.prompt = prompt;
	}

	public void completar(SalidaLlm s, ProveedorLlm.RespuestaLlm r) {
		this.veredicto = s.veredicto();
		this.confianza = s.confianza();
		this.justificacion = s.justificacion();
		this.explicacion = s.explicacion();
		this.recomendacion = s.recomendacion();
		this.codigoCorregido = s.codigoCorregido();
		this.modeloReal = r.modeloReal();
		this.tokensEntrada = r.tokensEntrada();
		this.tokensSalida = r.tokensSalida();
		this.costoReferenciaUsd = r.costoReferencia();
		this.latenciaMs = r.latenciaMs();
	}

	public void fallar(String error) {
		this.error = error;
	}

	public boolean isExitosa() {
		return veredicto != null;
	}

	public Long getId() {
		return id;
	}

	public Long getHallazgoId() {
		return hallazgoId;
	}

	public String getProveedor() {
		return proveedor;
	}

	public String getModelo() {
		return modelo;
	}

	public String getModeloReal() {
		return modeloReal;
	}

	public String getVersionPrompt() {
		return versionPrompt;
	}

	public String getPrompt() {
		return prompt;
	}

	public Veredicto getVeredicto() {
		return veredicto;
	}

	public Double getConfianza() {
		return confianza;
	}

	public String getJustificacion() {
		return justificacion;
	}

	public String getExplicacion() {
		return explicacion;
	}

	public String getRecomendacion() {
		return recomendacion;
	}

	/**
	 * Pasos de la recomendación cuando el LLM la numera en un solo párrafo ("1) … 2) …"), para mostrarlos como
	 * lista. Solo se reconoce una numeración correlativa desde 1; si no hay al menos dos pasos, lista vacía.
	 */
	public List<String> getPasosRecomendacion() {
		if (recomendacion == null) {
			return List.of();
		}
		Matcher m = NUMERO_PASO.matcher(recomendacion);
		List<Integer> inicios = new ArrayList<>();
		List<Integer> finales = new ArrayList<>();
		while (m.find()) {
			if (Integer.parseInt(m.group(1)) == inicios.size() + 1) {
				inicios.add(m.start(1));
				finales.add(m.end());
			}
		}
		if (inicios.size() < 2 || !recomendacion.substring(0, inicios.get(0)).isBlank()) {
			return List.of();
		}
		List<String> pasos = new ArrayList<>();
		for (int i = 0; i < inicios.size(); i++) {
			int fin = i + 1 < inicios.size() ? inicios.get(i + 1) : recomendacion.length();
			pasos.add(recomendacion.substring(finales.get(i), fin).strip());
		}
		return pasos;
	}

	public String getCodigoCorregido() {
		return codigoCorregido;
	}

	public Integer getTokensEntrada() {
		return tokensEntrada;
	}

	public Integer getTokensSalida() {
		return tokensSalida;
	}

	public Double getCostoReferenciaUsd() {
		return costoReferenciaUsd;
	}

	public Long getLatenciaMs() {
		return latenciaMs;
	}

	public String getError() {
		return error;
	}

	public LocalDateTime getFecha() {
		return fecha;
	}
}
