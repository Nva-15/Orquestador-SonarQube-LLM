package com.tesis.orquestador.metricas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.tesis.orquestador.etiquetado.Condicion;
import com.tesis.orquestador.etiquetado.Etiqueta;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.resultados.Corrida;

/** Libro Excel con todo lo necesario para el capítulo de resultados y su trazabilidad. */
@Component
public class ExportadorExcel {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public byte[] exportar(MetricasService.Informe informe) {
		try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
			var estilos = new Estilos(libro);
			resumen(libro.createSheet("Resumen"), informe, estilos);
			porRepositorio(libro.createSheet("Por repositorio"), informe, estilos);
			detalle(libro.createSheet("Detalle hallazgos"), informe.datos(), estilos);
			etiquetas(libro.createSheet("Etiquetas (ficha)"), informe.datos(), estilos);
			llamadas(libro.createSheet("Llamadas LLM"), informe.datos(), estilos);
			likert(libro.createSheet("Likert"), informe, estilos);
			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void resumen(Sheet hoja, MetricasService.Informe inf, Estilos e) {
		var f = new Filas(hoja, e);
		f.titulo("Métricas G1 (SonarQube aislado) vs G2 (SonarQube + orquestador + LLM)");
		f.fila("Generado", LocalDateTime.now().format(FECHA));
		f.fila("Análisis incluidos", inf.analisis().stream().map(a -> a.getProjectKey() + " (#" + a.getId() + ")")
				.collect(Collectors.joining(", ")));
		f.vacia();
		f.titulo("Verdad de referencia (etiquetado humano)");
		var et = inf.etiquetado();
		f.fila("Hallazgos", et.hallazgos());
		f.fila("Con al menos una etiqueta", et.etiquetados());
		f.fila("Con verdad de referencia (mayoría)", et.conVerdad());
		f.fila("Empates excluidos", et.empates());
		f.fila("VP / FP reales", et.vp() + " / " + et.fp());
		f.fila("Evaluadores", et.porEvaluador().entrySet().stream().map(x -> x.getKey() + " (" + x.getValue() + ")")
				.collect(Collectors.joining(", ")));
		f.filaNum("Acuerdo unánime", et.acuerdoPorcentual(), e.porcentaje);
		f.filaNum("Kappa de Fleiss", et.kappa(), e.decimal);
		f.fila("Interpretación (Landis y Koch)", et.getInterpretacionKappa());
		f.vacia();
		f.titulo("Comparación por corrida (mismos hallazgos)");
		f.cabecera("Corrida", "n", "Precisión G1", "Precisión G2", "Tasa FP G1 (%)", "Tasa FP G2 (%)",
				"Reducción FP (pp)", "VP descartados por error", "FP descartados", "Revisión humana",
				"VP conservados", "Exactitud LLM");
		for (var c : inf.comparaciones()) {
			Row r = f.nueva();
			f.texto(r, 0, c.corrida().getEtiqueta());
			f.numero(r, 1, c.n(), null);
			f.numero(r, 2, c.getPrecisionG1(), e.porcentaje);
			f.numero(r, 3, c.getPrecisionG2(), e.porcentaje);
			f.numero(r, 4, c.getTasaFpG1(), e.decimal);
			f.numero(r, 5, c.getTasaFpG2(), e.decimal);
			f.numero(r, 6, c.getReduccionFp(), e.decimal);
			f.numero(r, 7, c.vpDescartados(), null);
			f.numero(r, 8, c.fpDescartados(), null);
			f.numero(r, 9, c.revision(), null);
			f.numero(r, 10, c.getVpConservados(), e.porcentaje);
			f.numero(r, 11, c.getExactitudLlm(), e.porcentaje);
		}
		f.vacia();
		f.titulo("Costo y tiempo por corrida");
		f.cabecera("Corrida", "Respuestas", "Tokens entrada", "Tokens salida", "Coste referencia (USD)",
				"Latencia media (s)");
		for (var u : inf.usos()) {
			Row r = f.nueva();
			f.texto(r, 0, u.corrida().getEtiqueta());
			f.numero(r, 1, u.respuestas(), null);
			f.numero(r, 2, u.tokensEntrada(), null);
			f.numero(r, 3, u.tokensSalida(), null);
			f.numero(r, 4, u.costoReferenciaUsd(), e.dinero);
			f.numero(r, 5, u.latenciaMediaS(), e.decimal);
		}
		f.vacia();
		f.titulo("Definiciones");
		f.fila("Verdad de referencia", "Mayoría de los evaluadores humanos; empates excluidos. Nunca el veredicto del LLM.");
		f.fila("G1", "Todos los hallazgos de SonarQube se presentan como problemas.");
		f.fila("G2", "Se conservan los hallazgos que el LLM no marca como FALSO_POSITIVO (VP o revisión humana).");
		f.fila("Precisión", "VP / (VP + FP) sobre los hallazgos conservados.");
		f.fila("Tasa de FP", "FP / (FP + VP) × 100.");
		f.fila("VP descartados por error", "Hallazgos reales que el LLM marcó como falso positivo.");
		f.fila("Exactitud LLM", "Veredictos VP/FP del LLM que coinciden con la verdad (sin contar revisión humana).");
		hoja.setColumnWidth(0, 38 * 256);
		for (int i = 1; i < 12; i++) {
			hoja.setColumnWidth(i, 16 * 256);
		}
	}

	private void porRepositorio(Sheet hoja, MetricasService.Informe inf, Estilos e) {
		var f = new Filas(hoja, e);
		f.cabecera("Repositorio", "Análisis", "Hallazgos", "Con verdad", "Corrida", "n", "Precisión G1",
				"Precisión G2", "Tasa FP G1 (%)", "Tasa FP G2 (%)", "VP descartados por error");
		for (var rep : inf.porRepositorio()) {
			if (rep.comparaciones().isEmpty()) {
				Row r = f.nueva();
				f.texto(r, 0, rep.analisis().getProjectKey());
				f.numero(r, 1, rep.analisis().getId(), null);
				f.numero(r, 2, rep.etiquetado().hallazgos(), null);
				f.numero(r, 3, rep.etiquetado().conVerdad(), null);
				continue;
			}
			for (var c : rep.comparaciones()) {
				Row r = f.nueva();
				f.texto(r, 0, rep.analisis().getProjectKey());
				f.numero(r, 1, rep.analisis().getId(), null);
				f.numero(r, 2, rep.etiquetado().hallazgos(), null);
				f.numero(r, 3, rep.etiquetado().conVerdad(), null);
				f.texto(r, 4, c.corrida().getEtiqueta());
				f.numero(r, 5, c.n(), null);
				f.numero(r, 6, c.getPrecisionG1(), e.porcentaje);
				f.numero(r, 7, c.getPrecisionG2(), e.porcentaje);
				f.numero(r, 8, c.getTasaFpG1(), e.decimal);
				f.numero(r, 9, c.getTasaFpG2(), e.decimal);
				f.numero(r, 10, c.vpDescartados(), null);
			}
		}
		ajustar(hoja, 11);
	}

	private void detalle(Sheet hoja, MetricasService.Datos d, Estilos e) {
		var f = new Filas(hoja, e);
		Map<Corrida, Map<Long, Recomendacion>> corridas = d.corridas();
		Map<Long, List<Etiqueta>> ets = d.etiquetas();
		Map<Long, Condicion> verdad = CalculoMetricas.verdad(ets);
		List<String> cab = new java.util.ArrayList<>(List.of("Id", "Repositorio", "Clave Sonar", "Regla", "Nombre regla",
				"Tipo", "Severidad", "Archivo", "Línea", "Test", "Votos VP", "Votos FP", "Verdad de referencia"));
		corridas.keySet().forEach(c -> {
			cab.add("Veredicto " + c.getEtiqueta());
			cab.add("Confianza " + c.getEtiqueta());
		});
		f.cabecera(cab.toArray(String[]::new));
		for (Hallazgo h : d.hallazgos()) {
			Row r = f.nueva();
			List<Etiqueta> l = ets.getOrDefault(h.getId(), List.of());
			int col = 0;
			f.numero(r, col++, h.getId(), null);
			f.texto(r, col++, d.analisisDe(h) == null ? "" : d.analisisDe(h).getProjectKey());
			f.texto(r, col++, h.getClaveSonar());
			f.texto(r, col++, h.getRegla());
			f.texto(r, col++, h.getReglaNombre());
			f.texto(r, col++, h.getTipo());
			f.texto(r, col++, h.getSeveridad());
			f.texto(r, col++, h.getArchivo());
			f.numero(r, col++, h.getLineaInicio(), null);
			f.texto(r, col++, h.isEsTest() ? "sí" : "no");
			f.numero(r, col++, l.stream().filter(x -> x.getCondicion() == Condicion.VERDADERO_POSITIVO).count(), null);
			f.numero(r, col++, l.stream().filter(x -> x.getCondicion() == Condicion.FALSO_POSITIVO).count(), null);
			Condicion v = verdad.get(h.getId());
			f.texto(r, col++, v != null ? v.name() : l.isEmpty() ? "SIN_ETIQUETAR" : "EMPATE");
			for (var m : corridas.values()) {
				Recomendacion rec = m.get(h.getId());
				f.texto(r, col++, rec == null ? "" : rec.getVeredicto().name());
				f.numero(r, col++, rec == null ? null : rec.getConfianza(), e.decimal);
			}
		}
		ajustar(hoja, cab.size());
	}

	private void etiquetas(Sheet hoja, MetricasService.Datos d, Estilos e) {
		var f = new Filas(hoja, e);
		f.cabecera("Id hallazgo", "Repositorio", "Regla", "Archivo", "Línea", "Severidad", "Evaluador", "Condición",
				"Observaciones", "Fecha");
		Map<Long, Hallazgo> porId = d.hallazgos().stream().collect(Collectors.toMap(Hallazgo::getId, h -> h));
		d.etiquetas().values().stream().flatMap(List::stream)
				.sorted((x, y) -> x.getHallazgoId().compareTo(y.getHallazgoId()))
				.forEach(et -> {
					Hallazgo h = porId.get(et.getHallazgoId());
					Row r = f.nueva();
					f.numero(r, 0, et.getHallazgoId(), null);
					f.texto(r, 1, h == null || d.analisisDe(h) == null ? "" : d.analisisDe(h).getProjectKey());
					f.texto(r, 2, h == null ? "" : h.getRegla());
					f.texto(r, 3, h == null ? "" : h.getArchivo());
					f.numero(r, 4, h == null ? null : h.getLineaInicio(), null);
					f.texto(r, 5, h == null ? "" : h.getSeveridad());
					f.texto(r, 6, et.getEvaluador());
					f.texto(r, 7, et.getCondicion().name());
					f.texto(r, 8, et.getObservaciones());
					f.texto(r, 9, et.getFecha() == null ? "" : et.getFecha().format(FECHA));
				});
		ajustar(hoja, 10);
	}

	private void llamadas(Sheet hoja, MetricasService.Datos d, Estilos e) {
		var f = new Filas(hoja, e);
		f.cabecera("Id", "Id hallazgo", "Proveedor", "Modelo", "Modelo real", "Versión prompt", "Veredicto",
				"Confianza", "Tokens entrada", "Tokens salida", "Coste referencia (USD)", "Latencia (ms)", "Fecha",
				"Error");
		for (Recomendacion rec : d.todasLasLlamadas()) {
			Row r = f.nueva();
			f.numero(r, 0, rec.getId(), null);
			f.numero(r, 1, rec.getHallazgoId(), null);
			f.texto(r, 2, rec.getProveedor());
			f.texto(r, 3, rec.getModelo());
			f.texto(r, 4, rec.getModeloReal());
			f.texto(r, 5, rec.getVersionPrompt());
			f.texto(r, 6, rec.getVeredicto() == null ? "" : rec.getVeredicto().name());
			f.numero(r, 7, rec.getConfianza(), e.decimal);
			f.numero(r, 8, rec.getTokensEntrada(), null);
			f.numero(r, 9, rec.getTokensSalida(), null);
			f.numero(r, 10, rec.getCostoReferenciaUsd(), e.dinero);
			f.numero(r, 11, rec.getLatenciaMs(), null);
			f.texto(r, 12, rec.getFecha().format(FECHA));
			f.texto(r, 13, rec.getError());
		}
		ajustar(hoja, 14);
	}

	private void likert(Sheet hoja, MetricasService.Informe inf, Estilos e) {
		var f = new Filas(hoja, e);
		f.titulo("Utilidad percibida (Likert 1–5): media (desviación estándar)");
		f.cabecera("Corrida G2", "Participantes", "Condición", "Respuestas", "Claridad", "DE", "Pertinencia", "DE",
				"Accionabilidad", "DE", "Global", "DE");
		for (var l : inf.likert()) {
			for (var par : List.of(Map.entry("G1 · SonarQube", l.g1()), Map.entry("G2 · con LLM", l.g2()))) {
				var c = par.getValue();
				Row r = f.nueva();
				f.texto(r, 0, l.corrida());
				f.numero(r, 1, l.participantes(), null);
				f.texto(r, 2, par.getKey());
				f.numero(r, 3, c.respuestas(), null);
				int col = 4;
				for (var est : List.of(c.claridad(), c.pertinencia(), c.accionabilidad(), c.global())) {
					f.numero(r, col++, est.media(), e.decimal);
					f.numero(r, col++, est.desviacion(), e.decimal);
				}
			}
		}
		f.vacia();
		f.titulo("Respuestas individuales");
		f.cabecera("Cuestionario", "Corrida G2", "Participante", "Id hallazgo", "Condición", "Claridad", "Pertinencia",
				"Accionabilidad", "Media", "Comentario", "Fecha");
		for (var resp : inf.respuestasLikert()) {
			Row r = f.nueva();
			f.numero(r, 0, resp.getCuestionarioId(), null);
			f.texto(r, 1, inf.corridaDeCuestionario().get(resp.getCuestionarioId()));
			f.texto(r, 2, resp.getParticipante());
			f.numero(r, 3, resp.getHallazgoId(), null);
			f.texto(r, 4, resp.getCondicion());
			f.numero(r, 5, resp.getClaridad(), null);
			f.numero(r, 6, resp.getPertinencia(), null);
			f.numero(r, 7, resp.getAccionabilidad(), null);
			f.numero(r, 8, resp.getMedia(), e.decimal);
			f.texto(r, 9, resp.getComentario());
			f.texto(r, 10, resp.getFecha() == null ? "" : resp.getFecha().format(FECHA));
		}
		ajustar(hoja, 12);
	}

	private static void ajustar(Sheet hoja, int columnas) {
		hoja.createFreezePane(0, 1);
		for (int i = 0; i < columnas; i++) {
			hoja.setColumnWidth(i, 18 * 256);
		}
	}

	private static final class Estilos {

		final CellStyle negrita;
		final CellStyle cabecera;
		final CellStyle porcentaje;
		final CellStyle decimal;
		final CellStyle dinero;

		Estilos(XSSFWorkbook libro) {
			Font f = libro.createFont();
			f.setBold(true);
			negrita = libro.createCellStyle();
			negrita.setFont(f);
			cabecera = libro.createCellStyle();
			cabecera.setFont(f);
			cabecera.setWrapText(true);
			var formato = libro.createDataFormat();
			porcentaje = libro.createCellStyle();
			porcentaje.setDataFormat(formato.getFormat("0.0%"));
			decimal = libro.createCellStyle();
			decimal.setDataFormat(formato.getFormat("0.00"));
			dinero = libro.createCellStyle();
			dinero.setDataFormat(formato.getFormat("0.0000"));
		}
	}

	/** Escribe filas consecutivas en una hoja. */
	private static final class Filas {

		private final Sheet hoja;
		private final Estilos e;
		private int siguiente;

		Filas(Sheet hoja, Estilos e) {
			this.hoja = hoja;
			this.e = e;
		}

		Row nueva() {
			return hoja.createRow(siguiente++);
		}

		void vacia() {
			siguiente++;
		}

		void titulo(String texto) {
			var c = nueva().createCell(0);
			c.setCellValue(texto);
			c.setCellStyle(e.negrita);
		}

		void cabecera(String... columnas) {
			Row r = nueva();
			for (int i = 0; i < columnas.length; i++) {
				var c = r.createCell(i);
				c.setCellValue(columnas[i]);
				c.setCellStyle(e.cabecera);
			}
		}

		void fila(String etiqueta, Object valor) {
			Row r = nueva();
			texto(r, 0, etiqueta);
			if (valor instanceof Number n) {
				numero(r, 1, n, null);
			} else {
				texto(r, 1, valor == null ? "" : valor.toString());
			}
		}

		void filaNum(String etiqueta, Number valor, CellStyle estilo) {
			Row r = nueva();
			texto(r, 0, etiqueta);
			numero(r, 1, valor, estilo);
		}

		void texto(Row r, int col, String valor) {
			r.createCell(col).setCellValue(valor == null ? "" : valor);
		}

		void numero(Row r, int col, Number valor, CellStyle estilo) {
			var c = r.createCell(col);
			if (valor == null) {
				c.setCellValue("—");
				return;
			}
			c.setCellValue(valor.doubleValue());
			if (estilo != null) {
				c.setCellStyle(estilo);
			}
		}
	}
}
