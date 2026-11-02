package com.tesis.orquestador.etiquetado;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tesis.orquestador.analisis.AnalisisService;
import com.tesis.orquestador.escaneo.EspacioTrabajo;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;

/** Etiquetado humano ciego (verdad de referencia, OE4). Nunca consulta las respuestas del LLM. */
@Service
public class EtiquetadoService {

	public static final String PATRON_EVALUADOR = "[\\p{L}0-9 _.-]{1,40}";

	private final HallazgoRepository hallazgos;
	private final EtiquetaRepository etiquetas;
	private final AnalisisService analisis;
	private final EspacioTrabajo espacio;

	public EtiquetadoService(HallazgoRepository hallazgos, EtiquetaRepository etiquetas, AnalisisService analisis,
			EspacioTrabajo espacio) {
		this.hallazgos = hallazgos;
		this.etiquetas = etiquetas;
		this.analisis = analisis;
		this.espacio = espacio;
	}

	/**
	 * Hallazgos en orden aleatorio propio de cada evaluador (evita sesgos de orden) y reproducible: la semilla
	 * depende del evaluador y del análisis.
	 */
	public List<Hallazgo> ordenPara(Long analisisId, String evaluador) {
		List<Hallazgo> lista = new ArrayList<>(hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(analisisId));
		Collections.shuffle(lista, new Random(31L * evaluador.hashCode() + analisisId));
		return lista;
	}

	public Map<Long, Etiqueta> etiquetasDe(Long analisisId, String evaluador) {
		return etiquetas.findByAnalisisIdAndEvaluador(analisisId, evaluador).stream()
				.collect(Collectors.toMap(Etiqueta::getHallazgoId, Function.identity()));
	}

	public Optional<Etiqueta> etiqueta(Long hallazgoId, String evaluador) {
		return etiquetas.findByHallazgoIdAndEvaluador(hallazgoId, evaluador);
	}

	public void guardar(Hallazgo h, String evaluador, Condicion condicion, String observaciones) {
		Etiqueta e = etiquetas.findByHallazgoIdAndEvaluador(h.getId(), evaluador)
				.orElseGet(() -> new Etiqueta(h.getId(), h.getAnalisisId(), evaluador));
		e.registrar(condicion, observaciones);
		etiquetas.save(e);
	}

	/** Siguiente hallazgo sin etiquetar en el orden del evaluador, empezando después de {@code actual}. */
	public Optional<Hallazgo> siguientePendiente(Long analisisId, String evaluador, Long actual) {
		List<Hallazgo> orden = ordenPara(analisisId, evaluador);
		Map<Long, Etiqueta> hechas = etiquetasDe(analisisId, evaluador);
		int inicio = 0;
		for (int i = 0; i < orden.size(); i++) {
			if (orden.get(i).getId().equals(actual)) {
				inicio = i + 1;
			}
		}
		for (int k = 0; k < orden.size(); k++) {
			Hallazgo h = orden.get((inicio + k) % orden.size());
			if (!hechas.containsKey(h.getId())) {
				return Optional.of(h);
			}
		}
		return Optional.empty();
	}

	/** Archivo completo desde la copia de trabajo, por si el método no basta para decidir. */
	public List<Hallazgo.LineaCodigo> archivoCompleto(Hallazgo h) {
		String projectKey = analisis.obtener(h.getAnalisisId()).getProjectKey();
		Path raiz = espacio.ruta(projectKey);
		Path archivo = raiz.resolve(h.getArchivo()).normalize();
		if (!archivo.startsWith(raiz) || !Files.isRegularFile(archivo)) {
			return List.of();
		}
		List<String> lineas;
		try {
			lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			try {
				lineas = Files.readAllLines(archivo, StandardCharsets.ISO_8859_1);
			} catch (IOException ex) {
				return List.of();
			}
		} catch (IOException e) {
			return List.of();
		}
		int ini = h.getLineaInicio() != null ? h.getLineaInicio() : -1;
		int fin = h.getLineaFin() != null ? h.getLineaFin() : ini;
		List<Hallazgo.LineaCodigo> resultado = new ArrayList<>(lineas.size());
		for (int i = 0; i < lineas.size(); i++) {
			int n = i + 1;
			resultado.add(new Hallazgo.LineaCodigo(n, lineas.get(i), n >= ini && n <= fin));
		}
		return resultado;
	}

	/** Ficha de registro técnico en CSV (separador ";" y BOM para que Excel en español la abra bien). */
	public String fichaCsv(Long analisisId) {
		Map<Long, Hallazgo> porId = hallazgos.findByAnalisisIdOrderByArchivoAscLineaInicioAsc(analisisId).stream()
				.collect(Collectors.toMap(Hallazgo::getId, Function.identity()));
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("id;clave_sonar;regla;tipo;severidad;archivo;linea;evaluador;condicion;observaciones;fecha\n");
		for (Etiqueta e : etiquetas.findByAnalisisIdOrderByEvaluadorAscHallazgoIdAsc(analisisId)) {
			Hallazgo h = porId.get(e.getHallazgoId());
			if (h == null) {
				continue;
			}
			csv.append(String.join(";", List.of(
					String.valueOf(h.getId()), celda(h.getClaveSonar()), celda(h.getRegla()), celda(h.getTipo()),
					celda(h.getSeveridad()), celda(h.getArchivo()),
					h.getLineaInicio() == null ? "" : String.valueOf(h.getLineaInicio()),
					celda(e.getEvaluador()), e.getCondicion() == null ? "" : e.getCondicion().name(),
					celda(e.getObservaciones()), e.getFecha() == null ? "" : e.getFecha().toString())))
					.append('\n');
		}
		return csv.toString();
	}

	private static String celda(String valor) {
		if (valor == null) {
			return "";
		}
		String v = valor.replace("\r", " ").replace("\n", " ");
		return v.contains(";") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
	}
}
