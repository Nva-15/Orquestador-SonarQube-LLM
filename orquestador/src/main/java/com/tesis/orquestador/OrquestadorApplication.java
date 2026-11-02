package com.tesis.orquestador;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrquestadorApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrquestadorApplication.class, args);
	}

	/** Un solo hilo: los análisis se ejecutan de uno en uno (Sonar CE y Docker no se saturan). */
	@Bean(destroyMethod = "shutdownNow")
	ExecutorService ejecutorAnalisis() {
		return Executors.newSingleThreadExecutor(r -> new Thread(r, "analisis"));
	}

	/** Un solo hilo para el LLM: llamadas secuenciales, respetando los límites de uso del plan. */
	@Bean(destroyMethod = "shutdownNow")
	ExecutorService ejecutorLlm() {
		return Executors.newSingleThreadExecutor(r -> new Thread(r, "llm"));
	}
}
