package com.javaricci.config;

import com.javaricci.listener.JobCompletionListener;
import com.javaricci.tasklet.DownloadMunicipiosTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownloadJobConfig {

    public static final String JOB_NAME = "downloadMunicipiosJob";

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final AppProperties appProperties;

    public DownloadJobConfig(JobBuilderFactory jobBuilderFactory,
                              StepBuilderFactory stepBuilderFactory,
                              AppProperties appProperties) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.appProperties = appProperties;
    }

    @Bean
    public Job downloadMunicipiosJob(JobCompletionListener jobCompletionListener) {
        return jobBuilderFactory.get(JOB_NAME)
                .incrementer(new RunIdIncrementer())
                .listener(jobCompletionListener)
                .start(downloadStep())
                .build();
    }

    @Bean
    public Step downloadStep() {
        return stepBuilderFactory.get("downloadStep")
                .tasklet(new DownloadMunicipiosTasklet(appProperties))
                .build();
    }
}
