package com.tesis.orquestador.etiquetado;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisRepository;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.llm.ProveedorLlm;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.llm.SalidaLlm;
import com.tesis.orquestador.llm.Veredicto;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:etiquetado", "orquestador.sonar.token=" })
@AutoConfigureMockMvc
class EtiquetadoTest {

	private Long ANALISIS;

	@Autowired
	MockMvc mvc;

	@Autowired
	HallazgoRepository hallazgos;

	@Autowired
	RecomendacionRepository recomendaciones;

	@Autowired
	EtiquetaRepository etiquetas;

	@Autowired
	EtiquetadoService servicio;

	@Autowired
	AnalisisRepository analisis;

	private List<Hallazgo> lista;

	@BeforeEach
	void datos() {
		etiquetas.deleteAll();
		recomendaciones.deleteAll();
		hallazgos.deleteAll();
		ANALISIS = analisis.save(new Analisis("https://ejemplo/demo.git", "demo")).getId();
		lista = hallazgos.saveAll(List.of(
				new Hallazgo(ANALISIS, "k1", "java:S112", "CODE_SMELL", "MAJOR", "Excepción genérica", "A.java", 3, 3),
				new Hallazgo(ANALISIS, "k2", "java:S125", "CODE_SMELL", "MAJOR", "Código comentado", "B.java", 7, 7),
				new Hallazgo(ANALISIS, "k3", "java:S1192", "CODE_SMELL", "CRITICAL", "Literal duplicado", "C.java", 9, 9)));
		var r = new Recomendacion(lista.getFirst().getId(), "claude-code", "haiku", "v1", "prompt");
		r.completar(new SalidaLlm(Veredicto.FALSO_POSITIVO, 0.9, "JUSTIFICACION_SECRETA_IA", "EXPLICACION_IA",
				"RECOMENDACION_IA", ""), new ProveedorLlm.RespuestaLlm("{}", "claude-haiku", 1, 1, 0.0, 1));
		recomendaciones.save(r);
	}

	@Test
	void esCiegoNoMuestraNadaDelLlm() throws Exception {
		mvc.perform(get("/etiquetar/" + ANALISIS + "/" + lista.getFirst().getId()).cookie(new Cookie("evaluador", "E1")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Excepción genérica")))
				.andExpect(content().string(not(containsString("JUSTIFICACION_SECRETA_IA"))))
				.andExpect(content().string(not(containsString("RECOMENDACION_IA"))))
				.andExpect(content().string(not(containsString("Falso positivo ·"))));
	}

	@Test
	void ordenAleatorioReproducibleYDistintoPorEvaluador() {
		List<Long> e1 = ids(servicio.ordenPara(ANALISIS, "E1"));
		assertEquals(e1, ids(servicio.ordenPara(ANALISIS, "E1")));
		boolean algunoDistinto = List.of("E2", "E3", "E4", "E5").stream()
				.anyMatch(e -> !ids(servicio.ordenPara(ANALISIS, e)).equals(e1));
		assertEquals(true, algunoDistinto);
	}

	@Test
	void guardaYPasaAlSiguientePendiente() throws Exception {
		List<Long> orden = ids(servicio.ordenPara(ANALISIS, "E1"));
		var cookie = new Cookie("evaluador", "E1");

		mvc.perform(post("/etiquetar/" + ANALISIS + "/" + orden.get(0)).cookie(cookie)
				.param("condicion", "VERDADERO_POSITIVO").param("observaciones", "claro"))
				.andExpect(redirectedUrl("/etiquetar/" + ANALISIS + "/" + orden.get(1)));

		var e = etiquetas.findByHallazgoIdAndEvaluador(orden.get(0), "E1").orElseThrow();
		assertEquals(Condicion.VERDADERO_POSITIVO, e.getCondicion());
		assertEquals("claro", e.getObservaciones());
		assertNotEquals(true, etiquetas.findByHallazgoIdAndEvaluador(orden.get(0), "E2").isPresent());
	}

	@Test
	void exportaLaFichaEnCsv() throws Exception {
		servicio.guardar(lista.get(1), "E2", Condicion.FALSO_POSITIVO, "tiene ; punto y coma");

		mvc.perform(get("/etiquetar/" + ANALISIS + "/ficha.csv"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id;clave_sonar;regla;")))
				.andExpect(content().string(containsString("java:S125;CODE_SMELL;MAJOR;B.java;7;E2;FALSO_POSITIVO;\"tiene ; punto y coma\"")));
	}

	private static List<Long> ids(List<Hallazgo> hs) {
		return hs.stream().map(Hallazgo::getId).toList();
	}
}
