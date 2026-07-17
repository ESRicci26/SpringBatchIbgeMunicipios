package com.javaricci;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicacao.
 *
 * @EnableBatchProcessing registra toda a infraestrutura padrao do Spring Batch
 * (JobRepository, JobLauncher, JobExplorer, JobBuilderFactory, StepBuilderFactory),
 * usando automaticamente o DataSource marcado como @Primary no contexto
 * (batchDataSource, definido em DataSourceConfig / H2 em arquivo) para o
 * repositorio de metadados do Batch. Sem essa anotacao, os beans
 * JobBuilderFactory/StepBuilderFactory nao sao criados pelo Spring Boot.
 *
 * Ao subir, a aplicacao:
 * 1) inicializa o repositorio de metadados do Spring Batch (H2, em arquivo);
 * 2) inicializa o MunicipiosFileWatcher, que passa a monitorar continuamente
 *    o diretorio configurado (app.diretorio-monitorado, ex: E:\SpringBatch);
 * 3) expoe endpoints REST (/api/jobs/download e /api/jobs/etl) para disparo manual.
 *
 * Fluxo tipico de uso:
 *   a) chamar POST /api/jobs/download (ou rodar com --job=download)
 *      -> baixa municipiosbrasil.json da API publica e grava no diretorio monitorado;
 *   b) o MunicipiosFileWatcher detecta o arquivo gravado/estavel e dispara
 *      automaticamente o Job "etlMunicipiosJob";
 *   c) o Job cria a tabela MunicipiosBrasil dinamicamente (colunas extraidas do JSON)
 *      no banco SpringBatch.DB (gravado na raiz do projeto) e carrega todos os registros.
 */
@SpringBootApplication
@EnableBatchProcessing
public class SpringBatchIbgeMunicipiosApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchIbgeMunicipiosApplication.class, args);
    }
}
