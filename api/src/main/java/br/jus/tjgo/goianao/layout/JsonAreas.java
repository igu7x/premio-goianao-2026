package br.jus.tjgo.goianao.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * As areas sao persistidas como JSON em coluna VARCHAR — formato portavel entre
 * PostgreSQL e H2, e simples de versionar junto do layout.
 */
public final class JsonAreas {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonAreas() {}

    private abstract static class Base<T> implements AttributeConverter<T, String> {

        private final Class<T> tipo;

        protected Base(Class<T> tipo) {
            this.tipo = tipo;
        }

        @Override
        public String convertToDatabaseColumn(T atributo) {
            try {
                return atributo == null ? null : MAPPER.writeValueAsString(atributo);
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao serializar a área do layout.", e);
            }
        }

        @Override
        public T convertToEntityAttribute(String json) {
            try {
                return json == null || json.isBlank() ? null : MAPPER.readValue(json, tipo);
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao ler a área do layout: " + json, e);
            }
        }
    }

    @Converter
    public static class AreaTextoConverter extends Base<AreaTexto> {
        public AreaTextoConverter() {
            super(AreaTexto.class);
        }
    }

    @Converter
    public static class AreaCodigoConverter extends Base<AreaCodigo> {
        public AreaCodigoConverter() {
            super(AreaCodigo.class);
        }
    }
}
