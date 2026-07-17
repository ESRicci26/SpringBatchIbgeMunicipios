package com.javaricci.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ItemProcessor da etapa de ETL.
 *
 * Responsavel por normalizar cada registro antes da gravacao:
 * - remove espacos em branco extras de valores textuais;
 * - descarta registros sem "id" (protecao basica de qualidade de dados).
 *
 * Mantido simples de proposito, pois o objetivo central do desafio e a
 * arquitetura Reader -> Processor -> Writer completa do Spring Batch;
 * regras de negocio adicionais podem ser plugadas aqui.
 */
@Slf4j
public class MunicipioItemProcessor implements ItemProcessor<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> process(Map<String, Object> item) {
        Object id = item.get("id");
        if (id == null) {
            log.warn("Registro descartado por nao possuir 'id': {}", item);
            return null; // retornar null no ItemProcessor filtra o item (nao vai para o Writer)
        }

        Map<String, Object> normalizado = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : item.entrySet()) {
            Object valor = entry.getValue();
            if (valor instanceof String) {
                valor = ((String) valor).trim();
            }
            normalizado.put(entry.getKey(), valor);
        }
        return normalizado;
    }
}
