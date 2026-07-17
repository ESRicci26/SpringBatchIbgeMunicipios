package com.javaricci.tasklet;

import com.javaricci.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Tasklet que baixa o JSON de municipios do Brasil a partir de uma API publica
 * (por padrao, a API de localidades do IBGE) e grava o conteudo em
 * app.diretorio-monitorado/app.nome-arquivo-json (ex: E:\SpringBatch\municipiosbrasil.json).
 *
 * A gravacao do arquivo e quem efetivamente "dispara" o Job de ETL, atraves do
 * componente MunicipiosFileWatcher, que fica monitorando o mesmo diretorio.
 */
@Slf4j
public class DownloadMunicipiosTasklet implements Tasklet {

    private final AppProperties appProperties;
    private final HttpClient httpClient;

    public DownloadMunicipiosTasklet(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String url = appProperties.getApiMunicipiosUrl();
        Path diretorio = appProperties.getDiretorioMonitoradoPath();
        Path arquivoDestino = appProperties.getArquivoJsonPath();

        Files.createDirectories(diretorio);

        log.info("Baixando municipios de: {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Falha ao baixar arquivo. HTTP status = " + response.statusCode());
        }

        // Gravacao atomica: escreve em arquivo temporario e depois renomeia,
        // para que o FileWatcher so "veja" o arquivo final, ja completo.
        Path arquivoTemporario = diretorio.resolve(appProperties.getNomeArquivoJson() + ".tmp");
        Files.write(arquivoTemporario, response.body(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(arquivoTemporario, arquivoDestino,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Arquivo gravado com sucesso em: {} ({} bytes)",
                arquivoDestino.toAbsolutePath(), response.body().length);

        contribution.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }
}
