package com.javaricci.reader;

import com.javaricci.config.AppProperties;
import com.javaricci.util.JsonFlattenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ItemReader (com controle de ciclo de vida via ItemStream) responsavel por ler o array
 * do arquivo municipiosbrasil.json e devolver, item a item, cada municipio ja ACHATADO
 * (flatten) em um Map&lt;String,Object&gt; contendo exatamente as colunas descobertas
 * pelo PrepareTableTasklet (mesma ordem, com null para colunas ausentes no registro).
 *
 * E instanciado com escopo de STEP (ver EtlJobConfig), pois depende do valor calculado
 * dinamicamente no passo anterior (prepareTableStep) e publicado no JobExecutionContext.
 */
@Slf4j
public class MunicipioJsonItemReader implements ItemStreamReader<Map<String, Object>> {

    private final AppProperties appProperties;
    private final List<String> colunas;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Iterator<JsonNode> iteradorRegistros;
    private int totalLidos = 0;

    public MunicipioJsonItemReader(AppProperties appProperties, String colunasCsv) {
        this.appProperties = appProperties;
        this.colunas = Arrays.asList(colunasCsv.split(","));
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        File arquivo = appProperties.getArquivoJsonPath().toFile();
        try {
            JsonNode raiz = objectMapper.readTree(arquivo);
            this.iteradorRegistros = raiz.iterator();
            log.info("Reader aberto. Arquivo: {}", arquivo.getAbsolutePath());
        } catch (IOException e) {
            throw new ItemStreamException("Falha ao abrir/ler o arquivo JSON: " + arquivo.getAbsolutePath(), e);
        }
    }

    @Override
    public Map<String, Object> read() {
        if (iteradorRegistros == null || !iteradorRegistros.hasNext()) {
            log.info("Leitura concluida. Total de registros lidos: {}", totalLidos);
            return null; // sinaliza fim do Step para o Spring Batch
        }

        JsonNode registroJson = iteradorRegistros.next();
        Map<String, Object> valoresAchatados = JsonFlattenUtil.flatten(registroJson);

        // Garante que a linha tenha exatamente as colunas descobertas (na mesma ordem),
        // preenchendo com null quando o registro atual nao possuir determinado campo.
        Map<String, Object> linha = new LinkedHashMap<>();
        for (String coluna : colunas) {
            linha.put(coluna, valoresAchatados.get(coluna));
        }

        totalLidos++;
        return linha;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // Sem necessidade de checkpoint customizado: o proprio Spring Batch controla
        // o commit-interval via ExecutionContext do chunk.
    }

    @Override
    public void close() {
        iteradorRegistros = null;
    }
}
