package com.tesis.orquestador.metricas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.tesis.orquestador.etiquetado.Condicion;
import com.tesis.orquestador.etiquetado.Etiqueta;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.Veredicto;
import com.tesis.orquestador.resultados.Corrida;

/**
 * Métricas del diseño cuasiexperimental (OE4). Sin dependencias de Spring ni de la base de datos.
 * <ul>
 * <li>Verdad de referencia: mayoría de los evaluadores humanos; los empates se excluyen.</li>
 * <li>G1 (SonarQube aislado): todos los hallazgos se presentan como problemas.</li>
 * <li>G2 (Sonar + LLM): se conservan los hallazgos que el LLM no descarta (VP o revisión humana); los que marca
 * como FALSO_POSITIVO se descartan.</li>
 * <li>Precisión = VP/(VP+FP); tasa de FP = FP/(VP+FP)×100, sobre los hallazgos conservados.</li>
 * </ul>
 */
public final class CalculoMetricas {

	private CalculoMetricas() {
	}

	/** Verdad de referencia por hallazgo: VP, FP o null (sin etiquetas o empate). */
	public static Map<Long, Condicion> verdad(Map<Long, List<Etiqueta>> etiquetas) {
		Map<Long, Condicion> verdad = new LinkedHashMap<>();
		etiquetas.forEach((hallazgo, lista) -> {
			long vp = lista.stream().filter(e -> e.getCondicion() == Condicion.VERDADERO_POSITIVO).count();
			long fp = lista.stream().filter(e -> e.getCondicion() == Condicion.FALSO_POSITIVO).count();
			if (vp != fp) {
				verdad.put(hallazgo, vp > fp ? Condicion.VERDADERO_POSITIVO : Condicion.FALSO_POSITIVO);
			}
		});
		return verdad;
	}

	public static Etiquetado etiquetado(Collection<Hallazgo> hallazgos, Map<Long, List<Etiqueta>> etiquetas) {
		Set<String> evaluadores = new TreeSet<>();
		etiquetas.values().forEach(l -> l.forEach(e -> evaluadores.add(e.getEvaluador())));
		Map<String, Long> porEvaluador = new LinkedHashMap<>();
		for (String ev : evaluadores) {
			porEvaluador.put(ev, etiquetas.values().stream()
					.filter(l -> l.stream().anyMatch(e -> e.getEvaluador().equals(ev)))
					.count());
		}
		Map<Long, Condicion> verdad = verdad(etiquetas);
		long conAlguna = hallazgos.stream().filter(h -> !etiquetas.getOrDefault(h.getId(), List.of()).isEmpty()).count();
		long vp = verdad.values().stream().filter(c -> c == Condicion.VERDADERO_POSITIVO).count();

		// Acuerdo: solo hallazgos etiquetados por todos los evaluadores (requisito de kappa de Fleiss)
		List<int[]> completos = new ArrayList<>();
		for (List<Etiqueta> l : etiquetas.values()) {
			if (l.size() == evaluadores.size() && evaluadores.size() >= 2) {
				int nVp = (int) l.stream().filter(e -> e.getCondicion() == Condicion.VERDADERO_POSITIVO).count();
				completos.add(new int[] { nVp, l.size() - nVp });
			}
		}
		Double acuerdo = completos.isEmpty() ? null
				: (double) completos.stream().filter(c -> c[0] == 0 || c[1] == 0).count() / completos.size();
		return new Etiquetado(hallazgos.size(), conAlguna, verdad.size(), conAlguna - verdad.size(), vp,
				verdad.size() - vp, porEvaluador, completos.size(), acuerdo, kappaFleiss(completos));
	}

	/** Kappa de Fleiss para m evaluadores fijos y dos categorías; null si no está definido. */
	static Double kappaFleiss(List<int[]> items) {
		if (items.isEmpty()) {
			return null;
		}
		int m = items.getFirst()[0] + items.getFirst()[1];
		if (m < 2) {
			return null;
		}
		double pBarra = 0;
		double[] totalCategoria = new double[2];
		for (int[] it : items) {
			pBarra += (it[0] * (it[0] - 1.0) + it[1] * (it[1] - 1.0)) / (m * (m - 1.0));
			totalCategoria[0] += it[0];
			totalCategoria[1] += it[1];
		}
		pBarra /= items.size();
		double total = (double) items.size() * m;
		double pe = Math.pow(totalCategoria[0] / total, 2) + Math.pow(totalCategoria[1] / total, 2);
		return pe >= 1 ? null : (pBarra - pe) / (1 - pe);
	}

	/** Comparación G1 vs G2 sobre los hallazgos con verdad de referencia y respuesta de la corrida. */
	public static Comparacion comparar(Corrida corrida, Map<Long, Condicion> verdad, Map<Long, Recomendacion> recs) {
		int g1Vp = 0, g1Fp = 0, g2Vp = 0, g2Fp = 0, vpDescartados = 0, fpDescartados = 0, revision = 0, aciertos = 0;
		for (var e : verdad.entrySet()) {
			Recomendacion r = recs.get(e.getKey());
			if (r == null) {
				continue;
			}
			boolean esVp = e.getValue() == Condicion.VERDADERO_POSITIVO;
			Veredicto v = r.getVeredicto();
			if (esVp) {
				g1Vp++;
			} else {
				g1Fp++;
			}
			if (v == Veredicto.FALSO_POSITIVO) {
				if (esVp) {
					vpDescartados++;
				} else {
					fpDescartados++;
					aciertos++;
				}
			} else {
				if (esVp) {
					g2Vp++;
				} else {
					g2Fp++;
				}
				if (v == Veredicto.REQUIERE_REVISION_HUMANA) {
					revision++;
				} else if (esVp) {
					aciertos++;
				}
			}
		}
		return new Comparacion(corrida, g1Vp + g1Fp, g1Vp, g1Fp, g2Vp, g2Fp, vpDescartados, fpDescartados, revision,
				aciertos);
	}

	public static Uso uso(Corrida corrida, Collection<Recomendacion> recs) {
		long entrada = recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getTokensEntrada(), 0)).sum();
		long salida = recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getTokensSalida(), 0)).sum();
		double costo = recs.stream().mapToDouble(r -> Objects.requireNonNullElse(r.getCostoReferenciaUsd(), 0.0)).sum();
		double latencia = recs.stream().mapToLong(r -> Objects.requireNonNullElse(r.getLatenciaMs(), 0L)).average()
				.orElse(0) / 1000.0;
		return new Uso(corrida, recs.size(), entrada, salida, costo, latencia);
	}

	/** Utilidad percibida de una corrida: medias por dimensión en cada condición (G1 y G2). */
	public static Likert likert(String corrida, Collection<com.tesis.orquestador.likert.RespuestaLikert> respuestas) {
		var g1 = respuestas.stream().filter(r -> "G1".equals(r.getCondicion())).toList();
		var g2 = respuestas.stream().filter(r -> "G2".equals(r.getCondicion())).toList();
		long participantes = respuestas.stream().map(r -> r.getParticipante()).distinct().count();
		return new Likert(corrida, participantes, resumir(g1), resumir(g2));
	}

	private static LikertCondicion resumir(List<com.tesis.orquestador.likert.RespuestaLikert> rs) {
		return new LikertCondicion(rs.size(),
				Estadistico.de(rs.stream().mapToDouble(r -> r.getClaridad()).toArray()),
				Estadistico.de(rs.stream().mapToDouble(r -> r.getPertinencia()).toArray()),
				Estadistico.de(rs.stream().mapToDouble(r -> r.getAccionabilidad()).toArray()),
				Estadistico.de(rs.stream().mapToDouble(r -> r.getMedia()).toArray()));
	}

	/** Media y desviación estándar muestral (null si no hay datos). */
	public record Estadistico(Double media, Double desviacion) {

		static Estadistico de(double[] v) {
			if (v.length == 0) {
				return new Estadistico(null, null);
			}
			double media = java.util.Arrays.stream(v).average().orElse(0);
			Double sd = v.length < 2 ? null
					: Math.sqrt(java.util.Arrays.stream(v).map(x -> (x - media) * (x - media)).sum() / (v.length - 1));
			return new Estadistico(media, sd);
		}
	}

	public record LikertCondicion(long respuestas, Estadistico claridad, Estadistico pertinencia,
			Estadistico accionabilidad, Estadistico global) {
	}

	public record Likert(String corrida, long participantes, LikertCondicion g1, LikertCondicion g2) {

		/** Diferencia de la media global G2 − G1 (puntos de la escala 1–5). */
		public Double getDiferencia() {
			return g1.global().media() == null || g2.global().media() == null ? null
					: g2.global().media() - g1.global().media();
		}
	}

	static Double razon(long a, long b) {
		return b == 0 ? null : (double) a / b;
	}

	public record Etiquetado(long hallazgos, long etiquetados, long conVerdad, long empates, long vp, long fp,
			Map<String, Long> porEvaluador, long comparablesAcuerdo, Double acuerdoPorcentual, Double kappa) {

		/** Interpretación de Landis y Koch (1977). */
		public String getInterpretacionKappa() {
			if (kappa == null) {
				return "—";
			}
			if (kappa < 0) {
				return "pobre";
			}
			if (kappa <= 0.20) {
				return "leve";
			}
			if (kappa <= 0.40) {
				return "aceptable";
			}
			if (kappa <= 0.60) {
				return "moderado";
			}
			return kappa <= 0.80 ? "sustancial" : "casi perfecto";
		}
	}

	/**
	 * @param n             hallazgos comparables (con verdad de referencia y respuesta de la corrida)
	 * @param vpDescartados VP que el LLM descartó por error (marcó como FP)
	 * @param fpDescartados FP que el LLM descartó correctamente
	 * @param revision      hallazgos que el LLM envió a revisión humana (se conservan en G2)
	 * @param aciertos      veredictos VP/FP del LLM que coinciden con la verdad de referencia
	 */
	public record Comparacion(Corrida corrida, long n, long g1Vp, long g1Fp, long g2Vp, long g2Fp, long vpDescartados,
			long fpDescartados, long revision, long aciertos) {

		public Double getPrecisionG1() {
			return razon(g1Vp, g1Vp + g1Fp);
		}

		public Double getPrecisionG2() {
			return razon(g2Vp, g2Vp + g2Fp);
		}

		public Double getTasaFpG1() {
			Double p = getPrecisionG1();
			return p == null ? null : (1 - p) * 100;
		}

		public Double getTasaFpG2() {
			Double p = getPrecisionG2();
			return p == null ? null : (1 - p) * 100;
		}

		/** Puntos porcentuales de tasa de FP que elimina G2 respecto a G1. */
		public Double getReduccionFp() {
			return getTasaFpG1() == null || getTasaFpG2() == null ? null : getTasaFpG1() - getTasaFpG2();
		}

		/** Proporción de VP reales que G2 conserva (sensibilidad). */
		public Double getVpConservados() {
			return razon(g2Vp, g1Vp);
		}

		/** Exactitud del veredicto del LLM, sin contar los enviados a revisión humana. */
		public Double getExactitudLlm() {
			return razon(aciertos, n - revision);
		}
	}

	public record Uso(Corrida corrida, long respuestas, long tokensEntrada, long tokensSalida, double costoReferenciaUsd,
			double latenciaMediaS) {
	}
}
