package com.javaricci.watcher;

import com.javaricci.config.AppProperties;
import com.javaricci.config.EtlJobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Componente responsavel por MONITORAR o diretorio "E:\SpringBatch" (configuravel via
 * app.diretorio-monitorado), verificando periodicamente (polling) se o arquivo
 * municipiosbrasil.json foi criado ou alterado.
 *
 * IMPORTANTE - por que polling manual em vez de java.nio.file.WatchService:
 * O WatchService nativo do Java, no Windows, depende da API Win32
 * ReadDirectoryChangesW, que NAO funciona de forma confiavel (ou simplesmente nao
 * dispara nenhum evento) em unidades virtuais criadas com "subst", em alguns
 * compartilhamentos de rede mapeados como unidade (ex: "E:\" sendo uma unidade de
 * rede) e em determinados sistemas de arquivos. Isso faz o watcher "ligar" sem
 * nunca detectar nada, mesmo com o arquivo sendo gravado normalmente.
 * Fazendo polling direto (Files.size + Files.getLastModifiedTime) a cada N
 * milissegundos, o monitoramento funciona em qualquer tipo de unidade/drive,
 * local ou de rede, real ou virtual.
 *
 * Quando o arquivo e criado ou alterado, aguarda o tamanho parar de mudar
 * (garantindo que a gravacao/copia tenha terminado) e entao DISPARA
 * automaticamente o Job Spring Batch "etlMunicipiosJob".
 *
 * Roda em uma thread daemon dedicada durante todo o ciclo de vida da aplicacao.
 */
@Slf4j
@Component
public class MunicipiosFileWatcher {

    private final AppProperties appProperties;
    private final JobLauncher jobLauncher;
    private final Job etlMunicipiosJob;

    private volatile boolean executando = true;
    private Thread threadMonitor;

    /** Assinatura (tamanho + data de modificacao) do ULTIMO arquivo ja processado,
     *  para nao disparar o Job repetidamente para o mesmo arquivo inalterado. */
    private String assinaturaJaProcessada = null;

    public MunicipiosFileWatcher(AppProperties appProperties,
                                  JobLauncher jobLauncher,
                                  @Qualifier("etlMunicipiosJob") Job etlMunicipiosJob) {
        this.appProperties = appProperties;
        this.jobLauncher = jobLauncher;
        this.etlMunicipiosJob = etlMunicipiosJob;
    }

    @PostConstruct
    public void iniciar() {
        threadMonitor = new Thread(this::monitorarDiretorio, "municipios-file-watcher");
        threadMonitor.setDaemon(true);
        threadMonitor.start();
    }

    @PreDestroy
    public void parar() {
        executando = false;
        if (threadMonitor != null) {
            threadMonitor.interrupt();
        }
    }

    private void monitorarDiretorio() {
        Path diretorio = appProperties.getDiretorioMonitoradoPath();
        Path arquivoAlvo = appProperties.getArquivoJsonPath();
        long intervaloPollMs = appProperties.getWatcher().getIntervaloPollMs();

        try {
            Files.createDirectories(diretorio);
        } catch (Exception e) {
            log.error("Nao foi possivel criar/acessar o diretorio monitorado: {}", diretorio, e);
            return;
        }

        log.info("MunicipiosFileWatcher ativo (modo polling, a cada {} ms). Monitorando '{}'",
                intervaloPollMs, arquivoAlvo.toAbsolutePath());

        while (executando) {
            try {
                Thread.sleep(intervaloPollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            String assinaturaAtual = calcularAssinatura(arquivoAlvo);
            log.debug("Poll: arquivo='{}' assinatura='{}' ultimaProcessada='{}'",
                    arquivoAlvo, assinaturaAtual, assinaturaJaProcessada);

            if (assinaturaAtual == null) {
                continue; // arquivo ainda nao existe
            }

            if (assinaturaAtual.equals(assinaturaJaProcessada)) {
                continue; // nada mudou desde o ultimo processamento
            }

            log.info("Arquivo '{}' detectado/alterado (assinatura: {}). Aguardando estabilizacao...",
                    arquivoAlvo, assinaturaAtual);
            aguardarEstabilidadeEDispararJob(arquivoAlvo);
        }
    }

    /**
     * Evita disparar o Job com o arquivo ainda sendo escrito: aguarda a assinatura
     * (tamanho + data de modificacao) parar de mudar por "app.watcher.estabilidade-ms"
     * antes de dar o gatilho.
     */
    private void aguardarEstabilidadeEDispararJob(Path arquivo) {
        long intervaloEstabilidade = appProperties.getWatcher().getEstabilidadeMs();

        try {
            String assinaturaAnterior;
            String assinaturaAtual = calcularAssinatura(arquivo);

            do {
                assinaturaAnterior = assinaturaAtual;
                Thread.sleep(intervaloEstabilidade);
                assinaturaAtual = calcularAssinatura(arquivo);
            } while (assinaturaAtual != null && !assinaturaAtual.equals(assinaturaAnterior));

            if (assinaturaAtual == null) {
                log.warn("Arquivo '{}' desapareceu antes de estabilizar. Job NAO disparado.", arquivo);
                return;
            }

            log.info("Arquivo '{}' estavel (assinatura: {}). Disparando Job '{}'...",
                    arquivo, assinaturaAtual, EtlJobConfig.JOB_NAME);

            assinaturaJaProcessada = assinaturaAtual;
            dispararJobEtl();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gera uma "assinatura" simples do arquivo (tamanho_dataDeModificacao).
     * Retorna null se o arquivo nao existir ou nao puder ser lido.
     */
    private String calcularAssinatura(Path arquivo) {
        try {
            if (!Files.exists(arquivo) || !Files.isReadable(arquivo)) {
                return null;
            }
            long tamanho = Files.size(arquivo);
            long dataModificacao = Files.getLastModifiedTime(arquivo).toMillis();
            return tamanho + "_" + dataModificacao;
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized void dispararJobEtl() {
        try {
            JobParameters parametros = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("origem", "file-watcher")
                    .toJobParameters();
            jobLauncher.run(etlMunicipiosJob, parametros);
        } catch (Exception e) {
            log.error("Falha ao disparar o Job de ETL '{}'", EtlJobConfig.JOB_NAME, e);
        }
    }
}
