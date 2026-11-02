package com.tesis.orquestador.likert;

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
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.tesis.orquestador.analisis.Analisis;
import com.tesis.orquestador.analisis.AnalisisRepository;
import com.tesis.orquestador.analisis.EstadoAnalisis;
import com.tesis.orquestador.hallazgo.Hallazgo;
import com.tesis.orquestador.hallazgo.HallazgoRepository;
import com.tesis.orquestador.llm.ProveedorLlm;
import com.tesis.orquestador.llm.Recomendacion;
import com.tesis.orquestador.llm.RecomendacionRepository;
import com.tesis.orquestador.llm.SalidaLlm;
import com.tesis.orquestador.llm.Veredicto;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:likert", "orquestador.sonar.token=" })
@AutoConfigureMockMvc
class LikertTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	LikertService servicio;

	@Autowired
	AnalisisRepository analisis;

	@Autowired
	HallazgoRepository hallazgos;

	@Autowired
	RecomendacionRepository recomendaciones;

	private Cuestionario cuestionario;

	@BeforeEach
	void datos() {
		var a = new Analisis("https://ejemplo/demo.git", "demo-likert");
		a.setTotalHallazgos(4);
		a.finalizar(EstadoAnalisis.COMPLETADO);
		Long id = analisis.save(a).getId();
		for (int i = 1; i <= 4; i++) {
			var h = new Hallazgo(id, "k" + i, "java:S" + i, "CODE_SMELL", "MAJOR", "mensaje " + i, "A.java", i, i);
			h.describirRegla("Regla " + i, "DESC_SONAR_" + i);
			h = hallazgos.save(h);
			var r = new Recomendacion(h.getId(), "claude-code", "haiku", "v1", "prompt");
			r.completar(new SalidaLlm(Veredicto.FALSO_POSITIVO, 0.9, "JUSTIF_" + i, "EXPL_LLM_" + i, "RECO_LLM_" + i, ""),
					new ProveedorLlm.RespuestaLlm("{}", "claude-haiku", 1, 1, 0.0, 1));
			recomendaciones.save(r);
		}
		cuestionario = servicio.crear(id, "haiku_v1", 4);
		servicio.registrarParticipante("P1", "1 a 3 años");
		servicio.registrarParticipante("P2", "Más de 5 años");
	}

	@Test
	void contrabalanceaEntreParticipantes() {
		Map<Long, String> p1 = condiciones("P1");
		Map<Long, String> p2 = condiciones("P2");

		assertEquals(2, p1.values().stream().filter("G1"::equals).count());
		assertEquals(2, p2.values().stream().filter("G1"::equals).count());
		// Cada hallazgo se ve en G1 por uno y en G2 por el otro
		p1.forEach((item, cond) -> assertNotEquals(cond, p2.get(item)));
		// Reproducible
		assertEquals(p1, condiciones("P1"));
	}

	@Test
	void esCiegoCadaCondicionMuestraSoloSuTexto() throws Exception {
		for (var a : servicio.asignacion(cuestionario, "P1")) {
			int n = Integer.parseInt(a.item().getTextoG1().replaceAll("\\D+", " ").trim().split(" ")[1]);
			var pagina = mvc.perform(get("/likert/" + cuestionario.getId() + "/" + a.item().getId())
					.cookie(new Cookie("participante", "P1")))
					.andExpect(status().isOk())
					.andExpect(content().string(not(containsString("JUSTIF_"))))
					.andExpect(content().string(not(containsString("Falso positivo"))))
					.andExpect(content().string(not(containsString("haiku"))));
			if ("G1".equals(a.condicion())) {
				pagina.andExpect(content().string(containsString("DESC_SONAR_" + n)))
						.andExpect(content().string(not(containsString("EXPL_LLM_"))));
			} else {
				pagina.andExpect(content().string(containsString("EXPL_LLM_" + n)))
						.andExpect(content().string(not(containsString("DESC_SONAR_"))));
			}
		}
	}

	@Test
	void guardaLaRespuestaYApareceEnMetricas() throws Exception {
		var primero = servicio.asignacion(cuestionario, "P1").getFirst();
		mvc.perform(post("/likert/" + cuestionario.getId() + "/" + primero.item().getId())
				.cookie(new Cookie("participante", "P1"))
				.param("claridad", "5").param("pertinencia", "4").param("accionabilidad", "3"))
				.andExpect(redirectedUrl("/likert/" + cuestionario.getId()));

		var r = servicio.respuestasDe(cuestionario.getId(), "P1").get(primero.item().getId());
		assertEquals(primero.condicion(), r.getCondicion());
		assertEquals(4.0, r.getMedia());

		mvc.perform(get("/metricas"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("diferencia global G2 − G1")));
	}

	private Map<Long, String> condiciones(String participante) {
		List<LikertService.Asignado> asignacion = servicio.asignacion(cuestionario, participante);
		return asignacion.stream().collect(Collectors.toMap(a -> a.item().getId(), LikertService.Asignado::condicion));
	}
}
