package com.tesis.orquestador.hallazgo;

/** Fragmento de código que rodea un hallazgo y qué es (método, clase, fragmento). */
public record Contexto(String descripcion, int lineaInicio, String codigo) {
}
