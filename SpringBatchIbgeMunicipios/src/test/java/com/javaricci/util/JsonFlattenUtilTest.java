package com.javaricci.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFlattenUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveAchatarObjetoJsonAninhado() throws Exception {
        String json = "{"
                + "\"id\": 1100015,"
                + "\"nome\": \"Alta Floresta D'Oeste\","
                + "\"microrregiao\": {"
                + "  \"id\": 11006,"
                + "  \"nome\": \"Cacoal\","
                + "  \"mesorregiao\": {"
                + "    \"id\": 1102,"
                + "    \"nome\": \"Leste Rondoniense\","
                + "    \"UF\": { \"id\": 11, \"sigla\": \"RO\", \"nome\": \"Rondonia\" }"
                + "  }"
                + "}"
                + "}";

        JsonNode node = objectMapper.readTree(json);
        Map<String, Object> resultado = JsonFlattenUtil.flatten(node);

        assertThat(resultado)
                .containsEntry("id", 1100015L)
                .containsEntry("nome", "Alta Floresta D'Oeste")
                .containsEntry("microrregiao_id", 11006L)
                .containsEntry("microrregiao_mesorregiao_UF_sigla", "RO");
    }

    @Test
    void deveNormalizarHifenParaUnderscoreNoNomeDaColuna() {
        assertThat(JsonFlattenUtil.normalizarNomeColuna("regiao-imediata")).isEqualTo("regiao_imediata");
    }

    @Test
    void deveTratarCampoNuloComoColunaUnica() throws Exception {
        String json = "{ \"id\": 2, \"microrregiao\": null }";
        JsonNode node = objectMapper.readTree(json);
        Map<String, Object> resultado = JsonFlattenUtil.flatten(node);

        assertThat(resultado).containsEntry("microrregiao", null);
    }

    @Test
    void deveInferirTipoIntegerParaColunasTerminadasEmId() {
        assertThat(JsonFlattenUtil.inferirTipoColuna("id")).isEqualTo("INTEGER");
        assertThat(JsonFlattenUtil.inferirTipoColuna("microrregiao_id")).isEqualTo("INTEGER");
        assertThat(JsonFlattenUtil.inferirTipoColuna("nome")).isEqualTo("TEXT");
    }
}
