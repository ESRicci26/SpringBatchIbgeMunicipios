package com.javaricci.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runner opcional: permite disparar o Job de DOWNLOAD assim que a aplicacao sobe,
 * passando o argumento de linha de comando "--job=download".
 *
 * Exemplo:
 *   java -jar SpringBatchIbgeMunicipios.jar --job=download
 *
 * Sem esse argumento, a aplicacao apenas sobe e fica com o MunicipiosFileWatcher
 * ativo, aguardando o arquivo aparecer no diretorio monitorado (ou aguardando
 * chamadas manuais aos endpoints REST /api/jobs/download e /api/jobs/etl).
 */
@Slf4j
@Component
public class StartupRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final Job downloadMunicipiosJob;

    public StartupRunner(JobLauncher jobLauncher,
                          @Qualifier("downloadMunicipiosJob") Job downloadMunicipiosJob) {
        this.jobLauncher = jobLauncher;
        this.downloadMunicipiosJob = downloadMunicipiosJob;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("job") && args.getOptionValues("job").contains("download")) {
            log.info("Argumento --job=download recebido. Disparando Job de download na inicializacao...");
            jobLauncher.run(downloadMunicipiosJob, new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("origem", "startup")
                    .toJobParameters());
        } else {
            log.info("Aplicacao iniciada em modo monitor. Use POST /api/jobs/download para baixar o arquivo, " +
                    "ou aguarde o arquivo ser gravado manualmente em {} para o ETL ser disparado automaticamente.",
                    "app.diretorio-monitorado");
        }
    }
}
