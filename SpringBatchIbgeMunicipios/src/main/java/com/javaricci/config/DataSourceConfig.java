package com.javaricci.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Define os DOIS DataSources usados pela aplicacao:
 *
 * 1) "batchDataSource" (H2, arquivo, marcado como @Primary): repositorio de METADADOS
 *    do Spring Batch (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION, etc.).
 *    E o DataSource usado automaticamente pelo Spring Boot para o JobRepository/JobLauncher,
 *    por ser o unico marcado como @Primary.
 *
 * 2) "businessDataSource" (SQLite): banco de NEGOCIO, arquivo fisico "SpringBatch.DB"
 *    gravado na RAIZ DO PROJETO (diretorio de trabalho da aplicacao), onde a tabela
 *    MunicipiosBrasil e criada e populada. A URL JDBC e montada dinamicamente em
 *    tempo de execucao (ver businessDataSource()), e nao fixada no application.yml.
 */
@Configuration
public class DataSourceConfig {

    private final AppProperties appProperties;

    public DataSourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    @Primary
    public DataSource batchDataSource() throws IOException {
        garantirDiretorioExiste();
        AppProperties.DsConfig cfg = appProperties.getDatasource().getBatch();
        return DataSourceBuilder.create()
                .url(cfg.getUrl())
                .driverClassName(cfg.getDriverClassName())
                .username(cfg.getUsername())
                .password(cfg.getPassword())
                .build();
    }

    @Bean(name = "businessDataSource")
    public DataSource businessDataSource() {
        AppProperties.DsConfig cfg = appProperties.getDatasource().getBusiness();
        String urlSqlite = "jdbc:sqlite:" + appProperties.getArquivoBancoNegocioPath().toAbsolutePath();
        return DataSourceBuilder.create()
                .url(urlSqlite)
                .driverClassName(cfg.getDriverClassName())
                .build();
    }

    @Bean(name = "businessJdbcTemplate")
    public JdbcTemplate businessJdbcTemplate(@Qualifier("businessDataSource") DataSource businessDataSource) {
        return new JdbcTemplate(businessDataSource);
    }

    private void garantirDiretorioExiste() throws IOException {
        Files.createDirectories(appProperties.getDiretorioMonitoradoPath());
    }
}
