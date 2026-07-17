package com.javaricci.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints REST auxiliares para disparo MANUAL dos Jobs (uteis para testes,
 * demonstracao e operacao), complementando o disparo automatico feito pelo
 * MunicipiosFileWatcher.
 *
 * POST /api/jobs/download -> executa apenas o Job de download do JSON
 * POST /api/jobs/etl      -> executa apenas o Job de ETL (le o arquivo ja existente)
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job downloadMunicipiosJob;
    private final Job etlMunicipiosJob;

    public BatchJobController(JobLauncher jobLauncher,
                               @Qualifier("downloadMunicipiosJob") Job downloadMunicipiosJob,
                               @Qualifier("etlMunicipiosJob") Job etlMunicipiosJob) {
        this.jobLauncher = jobLauncher;
        this.downloadMunicipiosJob = downloadMunicipiosJob;
        this.etlMunicipiosJob = etlMunicipiosJob;
    }

    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> dispararDownload() throws Exception {
        JobExecution execucao = jobLauncher.run(downloadMunicipiosJob, parametrosUnicos("manual-download"));
        return ResponseEntity.ok(resumo(execucao));
    }

    @PostMapping("/etl")
    public ResponseEntity<Map<String, Object>> dispararEtl() throws Exception {
        JobExecution execucao = jobLauncher.run(etlMunicipiosJob, parametrosUnicos("manual-etl"));
        return ResponseEntity.ok(resumo(execucao));
    }

    private JobParameters parametrosUnicos(String origem) {
        return new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("origem", origem)
                .toJobParameters();
    }

    private Map<String, Object> resumo(JobExecution execucao) {
        return Map.of(
                "jobName", execucao.getJobInstance().getJobName(),
                "executionId", execucao.getId(),
                "status", execucao.getStatus().toString(),
                "exitStatus", execucao.getExitStatus().getExitCode()
        );
    }
}
