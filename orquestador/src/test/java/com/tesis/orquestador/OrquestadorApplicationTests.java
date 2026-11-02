package com.tesis.orquestador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:test",
		"orquestador.sonar.token=" })
@AutoConfigureMockMvc
class OrquestadorApplicationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void muestraLaPantallaDeNuevoAnalisis() throws Exception {
		mvc.perform(get("/analisis"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Nuevo análisis")))
				.andExpect(content().string(containsString("SONAR_TOKEN")));
	}

	@Test
	void sinTokenNoSeLanzaElAnalisis() throws Exception {
		mvc.perform(post("/analisis").param("origen", "D:\\repos\\demo"))
				.andExpect(redirectedUrl("/analisis"))
				.andExpect(flash().attribute("error", containsString("SONAR_TOKEN")));
	}
}
