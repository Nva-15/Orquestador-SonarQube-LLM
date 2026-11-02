package com.tesis.orquestador.metricas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tesis.orquestador.etiquetado.Condicion;
import com.tesis.orquestador.etiquetado.Etiqueta;
import com.tesis.orquestador.llm.ProveedorLlm;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.SalidaLlm;
import com.tesis.orquestador.llm.Veredicto;
import com.tesis.orquestador.resultados.Corrida;

class CalculoMetricasTest {

	private static final Condicion VP = Condicion.VERDADERO_POSITIVO;
	private static final Condicion FP = Condicion.FALSO_POSITIVO;
	private static final Corrida HAIKU = new Corrida("haiku", "v1");

	@Test
	void comparaG1YG2SobreLosMismosHallazgos() {
		// Verdad: 7 VP (ids 1-7) y 4 FP (ids 8-11)
		Map<Long, Condicion> verdad = new LinkedHashMap<>();
		Map<Long, Recomendacion> llm = new HashMap<>();
		for (long id = 1; id <= 5; id++) {
			verdad.put(id, VP);
			llm.put(id, rec(id, Veredicto.VERDADERO_POSITIVO));
		}
		verdad.put(6L, VP);
		llm.put(6L, rec(6, Veredicto.FALSO_POSITIVO)); // VP descartado por error
		verdad.put(7L, VP);
		llm.put(7L, rec(7, Veredicto.REQUIERE_REVISION_HUMANA)); // se conserva
		for (long id = 8; id <= 10; id++) {
			verdad.put(id, FP);
			llm.put(id, rec(id, Veredicto.FALSO_POSITIVO));
		}
		verdad.put(11L, FP);
		llm.put(11L, rec(11, Veredicto.VERDADERO_POSITIVO)); // FP que G2 no filtra
		verdad.put(12L, VP); // sin respuesta del LLM: fuera de la comparación

		var c = CalculoMetricas.comparar(HAIKU, verdad, llm);

		assertEquals(11, c.n());
		assertEquals(7.0 / 11, c.getPrecisionG1(), 1e-9);
		assertEquals(4.0 / 11 * 100, c.getTasaFpG1(), 1e-9);
		assertEquals(6.0 / 7, c.getPrecisionG2(), 1e-9); // conserva 5 VP + 1 revisión (VP) y 1 FP
		assertEquals(1.0 / 7 * 100, c.getTasaFpG2(), 1e-9);
		assertEquals(4.0 / 11 * 100 - 1.0 / 7 * 100, c.getReduccionFp(), 1e-9);
		assertEquals(1, c.vpDescartados());
		assertEquals(3, c.fpDescartados());
		assertEquals(1, c.revision());
		assertEquals(6.0 / 7, c.getVpConservados(), 1e-9);
		assertEquals(8.0 / 10, c.getExactitudLlm(), 1e-9); // 5 VP + 3 FP correctos, sin la revisión
	}

	@Test
	void sinHallazgosComparablesNoHayMetricas() {
		var c = CalculoMetricas.comparar(HAIKU, Map.of(1L, VP), Map.of());

		assertEquals(0, c.n());
		assertNull(c.getPrecisionG1());
		assertNull(c.getTasaFpG2());
		assertNull(c.getReduccionFp());
	}

	@Test
	void laVerdadEsLaMayoriaYLosEmpatesSeExcluyen() {
		Map<Long, List<Etiqueta>> etiquetas = Map.of(
				1L, List.of(et(1, "E1", VP), et(1, "E2", VP), et(1, "E3", FP)),
				2L, List.of(et(2, "E1", VP), et(2, "E2", FP)));

		var verdad = CalculoMetricas.verdad(etiquetas);

		assertEquals(VP, verdad.get(1L));
		assertEquals(false, verdad.containsKey(2L));
	}

	@Test
	void kappaDeFleissCoincideConElCalculoManual() {
		// 2 evaluadores; P̄ = 0,75; Pe = 0,625² + 0,375² = 0,53125 → κ = 0,21875 / 0,46875
		var kappa = CalculoMetricas.kappaFleiss(List.of(new int[] { 2, 0 }, new int[] { 2, 0 }, new int[] { 0, 2 },
				new int[] { 1, 1 }));
		assertEquals(0.21875 / 0.46875, kappa, 1e-9);
	}

	@Test
	void kappaIndefinidoSiTodosEligenLaMismaCategoria() {
		assertNull(CalculoMetricas.kappaFleiss(List.of(new int[] { 2, 0 }, new int[] { 2, 0 })));
	}

	private static Recomendacion rec(long hallazgo, Veredicto v) {
		var r = new Recomendacion(hallazgo, "claude-code", "haiku", "v1", "prompt");
		r.completar(new SalidaLlm(v, 0.9, "j", "e", "r", ""),
				new ProveedorLlm.RespuestaLlm("{}", "claude-haiku", 100, 50, 0.01, 1000));
		return r;
	}

	private static Etiqueta et(long hallazgo, String evaluador, Condicion c) {
		var e = new Etiqueta(hallazgo, 1L, evaluador);
		e.registrar(c, null);
		return e;
	}
}
