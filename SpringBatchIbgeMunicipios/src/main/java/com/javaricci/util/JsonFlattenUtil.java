package com.javaricci.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilitario responsavel por "achatar" (flatten) um JsonNode aninhado em um
 * Map&lt;String,Object&gt; de uma unica camada, onde a chave e formada pela
 * concatenacao do caminho de propriedades com "_" (ex: "microrregiao_mesorregiao_UF_sigla").
 *
 * E usado tanto para DESCOBRIR dinamicamente os nomes das colunas (a partir do proprio
 * arquivo municipiosbrasil.json) quanto para converter cada registro lido em uma linha
 * pronta para insercao no banco.
 */
public final class JsonFlattenUtil {

    private JsonFlattenUtil() {
    }

    /**
     * Achata um JsonNode (objeto) em um Map ordenado de chave->valor escalar.
     * Nomes de propriedades com hifen (ex: "regiao-imediata") sao normalizados para "_"
     * de forma a resultar em nomes de coluna validos em SQL.
     */
    public static Map<String, Object> flatten(JsonNode node) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        flattenInterno(node, "", resultado);
        return resultado;
    }

    private static void flattenInterno(JsonNode node, String prefixo, Map<String, Object> destino) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (!prefixo.isEmpty()) {
                destino.put(prefixo, null);
            }
            return;
        }

        if (node.isObject()) {
            Iterator<String> nomesCampos = node.fieldNames();
            while (nomesCampos.hasNext()) {
                String campo = nomesCampos.next();
                String campoNormalizado = normalizarNomeColuna(campo);
                String novoPrefixo = prefixo.isEmpty() ? campoNormalizado : prefixo + "_" + campoNormalizado;
                flattenInterno(node.get(campo), novoPrefixo, destino);
            }
        } else if (node.isValueNode()) {
            Object valor;
            if (node.isIntegralNumber()) {
                valor = node.asLong();
            } else if (node.isFloatingPointNumber()) {
                valor = node.asDouble();
            } else if (node.isBoolean()) {
                valor = node.asBoolean();
            } else {
                valor = node.asText();
            }
            destino.put(prefixo, valor);
        }
        // Arrays aninhados nao sao esperados no municipiosbrasil.json; caso existam,
        // sao ignorados de forma segura (nao fazem parte do escopo deste layout).
    }

    /**
     * Normaliza um nome de propriedade JSON para um nome de coluna SQL seguro:
     * troca hifens por underscore. Mantem maiusculas/minusculas originais (ex: "UF").
     */
    public static String normalizarNomeColuna(String nomeCampo) {
        return nomeCampo.replace('-', '_');
    }

    /**
     * Varre TODOS os registros de um array JSON e retorna a uniao ordenada de todas
     * as colunas encontradas (alguns registros podem nao ter todos os campos,
     * ex: municipios sem microrregiao/mesorregiao preenchidas).
     */
    public static Set<String> descobrirColunas(Iterable<JsonNode> registros) {
        Set<String> colunas = new LinkedHashSet<>();
        for (JsonNode registro : registros) {
            colunas.addAll(flatten(registro).keySet());
        }
        return colunas;
    }

    /**
     * Infere um tipo de coluna simples para o DDL, baseado no nome da coluna:
     * colunas terminadas em "id" (ex: id, microrregiao_id) viram INTEGER,
     * as demais viram TEXT. O SQLite possui tipagem dinamica (type affinity),
     * entao esta inferencia serve principalmente como documentacao do schema.
     */
    public static String inferirTipoColuna(String nomeColuna) {
        String minusculo = nomeColuna.toLowerCase();
        if (minusculo.equals("id") || minusculo.endsWith("_id")) {
            return "INTEGER";
        }
        return "TEXT";
    }
}
