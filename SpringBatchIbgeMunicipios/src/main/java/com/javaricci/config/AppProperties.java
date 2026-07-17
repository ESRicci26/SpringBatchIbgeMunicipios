package com.javaricci.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Propriedades de configuracao da aplicacao, mapeadas a partir de "app.*" no application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Diretorio monitorado e onde o arquivo JSON e gravado. Ex: E:/SpringBatch */
    private String diretorioMonitorado;

    /** Nome do arquivo JSON. Ex: municipiosbrasil.json */
    private String nomeArquivoJson;

    /** URL da API publica de onde o JSON de municipios e baixado. */
    private String apiMunicipiosUrl;

    /** Nome da tabela de destino no banco de negocio. */
    private String nomeTabela;

    /**
     * Nome do arquivo do banco de negocio (SQLite), ex: "SpringBatch.DB".
     * Sempre resolvido a partir da RAIZ DO PROJETO (diretorio de trabalho da JVM -
     * "user.dir"), independente de onde o arquivo JSON monitorado esta configurado.
     */
    private String nomeArquivoBanco;

    @NestedConfigurationProperty
    private Watcher watcher = new Watcher();

    @NestedConfigurationProperty
    private DataSourceProps datasource = new DataSourceProps();

    public Path getDiretorioMonitoradoPath() {
        return Paths.get(diretorioMonitorado);
    }

    public Path getArquivoJsonPath() {
        return getDiretorioMonitoradoPath().resolve(nomeArquivoJson);
    }

    /**
     * Caminho absoluto do arquivo SpringBatch.DB, sempre na RAIZ DO PROJETO
     * (diretorio a partir do qual a aplicacao foi iniciada).
     */
    public Path getArquivoBancoNegocioPath() {
        return Paths.get(System.getProperty("user.dir")).resolve(nomeArquivoBanco);
    }

    @Data
    public static class Watcher {
        private long intervaloPollMs = 2000L;
        private long estabilidadeMs = 1500L;
    }

    @Data
    public static class DataSourceProps {
        @NestedConfigurationProperty
        private DsConfig batch = new DsConfig();
        @NestedConfigurationProperty
        private DsConfig business = new DsConfig();
    }

    @Data
    public static class DsConfig {
        private String url;
        private String driverClassName;
        private String username;
        private String password;
    }
}
