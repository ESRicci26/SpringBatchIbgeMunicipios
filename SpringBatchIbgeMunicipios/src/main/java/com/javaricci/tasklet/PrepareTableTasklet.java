package com.javaricci.tasklet;

import com.javaricci.config.AppProperties;
import com.javaricci.util.JsonFlattenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Primeiro passo do Job de ETL.
 *
 * Le o arquivo municipiosbrasil.json, DESCOBRE dinamicamente os nomes das colunas
 * (a partir das proprias chaves do JSON, achatando a estrutura aninhada) e
 * (re)cria a tabela MunicipiosBrasil no banco de negocio (SpringBatch.DB) com essas colunas.
 *
 * A lista final de colunas e publicada no ExecutionContext do Job para ser
 * reaproveitada pelo Reader e pelo Writer do passo seguinte (etlStep).
 */
@Slf4j
public class PrepareTableTasklet implements Tasklet {

    public static final String CHAVE_COLUNAS_CONTEXTO = "colunasMunicipiosBrasil";

    private final AppProperties appProperties;
    private final JdbcTemplate businessJdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrepareTableTasklet(AppProperties appProperties, JdbcTemplate businessJdbcTemplate) {
        this.appProperties = appProperties;
        this.businessJdbcTemplate = businessJdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        File arquivo = appProperties.getArquivoJsonPath().toFile();
        log.info("Lendo arquivo '{}' para descoberta dinamica das colunas...", arquivo.getAbsolutePath());

        if (!arquivo.exists()) {
            throw new IllegalStateException("Arquivo nao encontrado: " + arquivo.getAbsolutePath());
        }

        JsonNode raiz = objectMapper.readTree(arquivo);
        if (!raiz.isArray() || raiz.isEmpty()) {
            throw new IllegalStateException("O arquivo JSON esta vazio ou nao e um array de municipios.");
        }

        List<JsonNode> registros = new ArrayList<>();
        raiz.forEach(registros::add);

        Set<String> colunas = JsonFlattenUtil.descobrirColunas(registros);
        log.info("Total de registros: {} | Total de colunas descobertas: {}", registros.size(), colunas.size());
        log.info("Colunas: {}", colunas);

        criarTabela(colunas);

        // Publica a lista ordenada de colunas no ExecutionContext do Job,
        // para ser reutilizada pelo Reader (flatten) e pelo Writer (INSERT dinamico).
        String colunasCsv = String.join(",", colunas);
        chunkContext.getStepContext().getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .putString(CHAVE_COLUNAS_CONTEXTO, colunasCsv);

        contribution.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }

    private void criarTabela(Set<String> colunas) {
        String nomeTabela = appProperties.getNomeTabela();

        String definicaoColunas = colunas.stream()
                .map(coluna -> {
                    String tipo = JsonFlattenUtil.inferirTipoColuna(coluna);
                    String constraint = coluna.equals("id") ? " PRIMARY KEY" : "";
                    return "\"" + coluna + "\" " + tipo + constraint;
                })
                .collect(Collectors.joining(", "));

        String dropDdl = "DROP TABLE IF EXISTS " + nomeTabela;
        String createDdl = "CREATE TABLE " + nomeTabela + " (" + definicaoColunas + ")";

        log.info("Recriando tabela de destino: {}", createDdl);
        businessJdbcTemplate.execute(dropDdl);
        businessJdbcTemplate.execute(createDdl);
    }
}
