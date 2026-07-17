package com.javaricci.writer;

import com.javaricci.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ItemWriter da etapa de ETL.
 *
 * Monta e executa um INSERT dinamico (batch) na tabela MunicipiosBrasil, usando
 * exatamente as colunas descobertas pelo PrepareTableTasklet a partir do arquivo JSON.
 *
 * Este projeto usa Spring Batch 4.3.x (Spring Boot 2.7 / Java 11), cuja interface
 * ItemWriter#write recebe List&lt;? extends T&gt;.
 */
@Slf4j
public class MunicipioItemWriter implements ItemWriter<Map<String, Object>> {

    private final AppProperties appProperties;
    private final JdbcTemplate businessJdbcTemplate;
    private final List<String> colunas;
    private final String sqlInsert;

    public MunicipioItemWriter(AppProperties appProperties, JdbcTemplate businessJdbcTemplate, String colunasCsv) {
        this.appProperties = appProperties;
        this.businessJdbcTemplate = businessJdbcTemplate;
        this.colunas = Arrays.asList(colunasCsv.split(","));
        this.sqlInsert = montarSqlInsert();
    }

    private String montarSqlInsert() {
        String nomeTabela = appProperties.getNomeTabela();
        String nomesColunas = colunas.stream()
                .map(c -> "\"" + c + "\"")
                .collect(Collectors.joining(", "));
        String placeholders = colunas.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + nomeTabela + " (" + nomesColunas + ") VALUES (" + placeholders + ")";
    }

    @Override
    public void write(List<? extends Map<String, Object>> itens) {
        List<Object[]> lotes = new ArrayList<>(itens.size());
        for (Map<String, Object> item : itens) {
            Object[] valores = new Object[colunas.size()];
            for (int i = 0; i < colunas.size(); i++) {
                valores[i] = item.get(colunas.get(i));
            }
            lotes.add(valores);
        }

        businessJdbcTemplate.batchUpdate(sqlInsert, lotes);
        log.info("Chunk gravado com sucesso: {} registro(s).", lotes.size());
    }
}
