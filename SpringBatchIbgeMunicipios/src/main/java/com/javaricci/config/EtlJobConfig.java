package com.javaricci.config;

import com.javaricci.listener.JobCompletionListener;
import com.javaricci.processor.MunicipioItemProcessor;
import com.javaricci.reader.MunicipioJsonItemReader;
import com.javaricci.tasklet.PrepareTableTasklet;
import com.javaricci.writer.MunicipioItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/**
 * Job 2: ETL (extract / transform / load) do arquivo municipiosbrasil.json.
 *
 * Composto por 2 Steps, executados em sequencia:
 *
 * 1) prepareTableStep (Tasklet): le o arquivo JSON, descobre dinamicamente as colunas
 *    (a partir das proprias chaves do JSON) e (re)cria a tabela MunicipiosBrasil no
 *    banco SpringBatch.DB. Publica a lista de colunas no JobExecutionContext.
 *
 * 2) etlStep (chunk-oriented, commit-interval configuravel): le -> processa -> grava
 *    cada municipio, em lotes, usando exatamente as colunas descobertas no passo anterior.
 *
 * Este Job e disparado automaticamente pelo MunicipiosFileWatcher assim que o
 * arquivo municipiosbrasil.json e detectado (e considerado estavel) no diretorio monitorado.
 */
@Configuration
public class EtlJobConfig {

    public static final String JOB_NAME = "etlMunicipiosJob";
    private static final int COMMIT_INTERVAL = 200;

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final AppProperties appProperties;
    private final JdbcTemplate businessJdbcTemplate;

    public EtlJobConfig(JobBuilderFactory jobBuilderFactory,
                         StepBuilderFactory stepBuilderFactory,
                         AppProperties appProperties,
                         @Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbcTemplate) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.appProperties = appProperties;
        this.businessJdbcTemplate = businessJdbcTemplate;
    }

    @Bean
    public Job etlMunicipiosJob(JobCompletionListener jobCompletionListener) {
        return jobBuilderFactory.get(JOB_NAME)
                .incrementer(new RunIdIncrementer())
                .listener(jobCompletionListener)
                .start(prepareTableStep())
                .next(etlStep())
                .build();
    }

    // ------------------------------------------------------------------
    // Step 1 - Tasklet: descoberta de colunas + (re)criacao da tabela
    // ------------------------------------------------------------------
    @Bean
    public Step prepareTableStep() {
        return stepBuilderFactory.get("prepareTableStep")
                .tasklet(new PrepareTableTasklet(appProperties, businessJdbcTemplate))
                .build();
    }

    // ------------------------------------------------------------------
    // Step 2 - Chunk: Reader -> Processor -> Writer
    // ------------------------------------------------------------------
    @Bean
    public Step etlStep() {
        return stepBuilderFactory.get("etlStep")
                .<Map<String, Object>, Map<String, Object>>chunk(COMMIT_INTERVAL)
                .reader(municipioJsonItemReader(null))
                .processor(municipioItemProcessor())
                .writer(municipioItemWriter(null))
                .build();
    }

    /**
     * Reader com escopo de STEP: le a lista de colunas descoberta no passo anterior
     * diretamente do JobExecutionContext (late binding via SpEL), garantindo que o
     * arquivo so seja aberto no momento da execucao do Step (nunca na configuracao).
     */
    @Bean
    @StepScope
    public MunicipioJsonItemReader municipioJsonItemReader(
            @Value("#{jobExecutionContext['" + PrepareTableTasklet.CHAVE_COLUNAS_CONTEXTO + "']}") String colunasCsv) {
        return new MunicipioJsonItemReader(appProperties, colunasCsv);
    }

    @Bean
    public MunicipioItemProcessor municipioItemProcessor() {
        return new MunicipioItemProcessor();
    }

    /**
     * Writer com escopo de STEP, pelo mesmo motivo do Reader: precisa da lista de
     * colunas descoberta dinamicamente no prepareTableStep para montar o INSERT.
     */
    @Bean
    @StepScope
    public MunicipioItemWriter municipioItemWriter(
            @Value("#{jobExecutionContext['" + PrepareTableTasklet.CHAVE_COLUNAS_CONTEXTO + "']}") String colunasCsv) {
        return new MunicipioItemWriter(appProperties, businessJdbcTemplate, colunasCsv);
    }
}
