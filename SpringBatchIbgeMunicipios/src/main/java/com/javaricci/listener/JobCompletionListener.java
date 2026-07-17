package com.javaricci.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

/**
 * Listener generico, reaproveitado pelos dois Jobs (download e ETL), responsavel por
 * registrar em log o inicio/fim de cada execucao, tempo total e status final.
 */
@Slf4j
@Component
public class JobCompletionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info(">>> Iniciando Job '{}' (executionId={})",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Date inicio = jobExecution.getStartTime();
        Date fim = jobExecution.getEndTime() != null ? jobExecution.getEndTime() : new Date();
        Duration duracao = Duration.between(inicio.toInstant(), fim.toInstant());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("<<< Job '{}' finalizado com SUCESSO em {} ms (executionId={})",
                    jobExecution.getJobInstance().getJobName(), duracao.toMillis(), jobExecution.getId());
        } else {
            log.error("<<< Job '{}' finalizado com FALHA. Status={} (executionId={})",
                    jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(), jobExecution.getId());
            jobExecution.getAllFailureExceptions().forEach(ex -> log.error("Causa da falha:", ex));
        }
    }
}
