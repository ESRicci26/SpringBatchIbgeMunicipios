# SpringBatchIbgeMunicipios

Aplicação **Spring Batch** (Maven, **Java 11** com todo o fluxo da arquitetura padrão do Spring Batch: `Job` → `Step` → (`Tasklet` | `ItemReader`/`ItemProcessor`/`ItemWriter`), `JobRepository`, `JobLauncher`, execução em *chunks* e *listeners*.

1. **Baixa** o arquivo `municipiosbrasil.json` de uma API pública (IBGE) e grava em `E:\SpringBatch\municipiosbrasil.json`, nessa aplicação eu copiei o arquivo na pasta municipiosbrasil.json, pois a API do IBGE não tem download;
2. **Monitora** esse diretório em tempo real; assim que o arquivo é gravado (e estabiliza), **dispara automaticamente** um Job Spring Batch;
3. Esse Job **lê o arquivo inteiro**, **descobre dinamicamente as colunas** a partir das próprias chaves do JSON (fazendo o *flatten* da estrutura aninhada), cria o banco **`SpringBatch.DB`** — gravado **na raiz do projeto** — com a tabela `MunicipiosBrasil` e **carrega todos os registros**.

---

## 1. Arquitetura

```
┌─────────────────────────────────┐   grava arquivo   ┌──────────────────────────────────┐
│ Job 1: downloadMunicipiosJob     │ ─────────────────▶ │ E:\SpringBatch\municipiosbrasil.json │
│ Step: downloadStep (Tasklet)     │                     └──────────────────────────────────┘
│ -> chama a API do IBGE via       │                                    │
│    java.net.http.HttpClient      │                                    │ detectado por
└─────────────────────────────────┘                                    ▼
                                                          ┌──────────────────────────┐
                                                          │  MunicipiosFileWatcher    │
                                                          │  (polling de arquivo)     │
                                                          └──────────────────────────┘
                                                                     │ dispara
                                                                     ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ Job 2: etlMunicipiosJob                                                                 │
│                                                                                           │
│ Step 1: prepareTableStep (Tasklet)                                                      │
│   - lê o JSON inteiro, achata (flatten) cada registro                                   │
│   - descobre a UNIÃO de todas as colunas encontradas no arquivo                         │
│   - (re)cria a tabela MunicipiosBrasil em SpringBatch.DB (SQLite, na RAIZ DO PROJETO)   │
│   - publica a lista de colunas no JobExecutionContext                                   │
│                                                                                           │
│ Step 2: etlStep (chunk-oriented, commit-interval=200)                                   │
│   Reader  (MunicipioJsonItemReader)  -> lê e achata cada município                      │
│   Processor (MunicipioItemProcessor) -> normaliza/valida                                │
│   Writer  (MunicipioItemWriter)      -> INSERT em lote (batchUpdate) via JDBC           │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### Onde fica cada arquivo

| Arquivo | Local | Observação |
|---|---|---|
| `municipiosbrasil.json` | `E:\SpringBatch\` | Baixado pelo Job 1 e monitorado pelo watcher |
| `batch-metadata.mv.db` | `E:\SpringBatch\` | Metadados internos do Spring Batch (H2) |
| **`SpringBatch.DB`** | **raiz do projeto** (diretório de trabalho da JVM) | Banco de negócio (SQLite), tabela `MunicipiosBrasil` |

> Ou seja: ao rodar `java -jar target/SpringBatchIbgeMunicipios.jar` **de dentro da pasta raiz do projeto**, o arquivo `SpringBatch.DB` é criado ali mesmo, ao lado do `pom.xml`. Isso é resolvido dinamicamente em tempo de execução via `System.getProperty("user.dir")` (veja `DataSourceConfig`), e não por um caminho fixo.

### Por que dois bancos?

| Banco | Tecnologia | Local | Finalidade |
|---|---|---|---|
| **Metadados do Spring Batch** | H2 (embarcado) | `E:\SpringBatch\batch-metadata.mv.db` | Tabelas internas `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc. |
| **Negócio** | SQLite | `<raiz do projeto>\SpringBatch.DB` | Tabela `MunicipiosBrasil` |

Manter os metadados do Spring Batch separados do banco de negócio é a prática recomendada: evita conflito com o schema interno do Batch e permite trocar o banco de negócio livremente (aqui, SQLite) sem afetar o `JobRepository`.

### Como as colunas são descobertas

O arquivo `municipiosbrasil.json` tem estrutura aninhada (padrão da API do IBGE):

```json
{
  "id": 1100015,
  "nome": "Alta Floresta D'Oeste",
  "microrregiao": {
    "id": 11006,
    "nome": "Cacoal",
    "mesorregiao": { "id": 1102, "nome": "Leste Rondoniense", "UF": { "id": 11, "sigla": "RO", "nome": "Rondônia", "regiao": {...} } }
  },
  "regiao-imediata": { "id": 110005, "nome": "Cacoal", "regiao-intermediaria": {...} }
}
```

A classe `JsonFlattenUtil` percorre recursivamente cada objeto e gera nomes de coluna concatenando o caminho com `_` (ex.: `microrregiao_mesorregiao_UF_sigla`), trocando `-` por `_` (ex.: `regiao-imediata` → `regiao_imediata`). O `PrepareTableTasklet` varre **todos** os registros do arquivo, calcula a **união** de todas as colunas encontradas (alguns municípios, como Fernando de Noronha, não têm `microrregiao` preenchida) e gera o `CREATE TABLE` dinamicamente — ou seja, **as colunas realmente vêm do arquivo**, não são fixadas no código.

Resultado esperado (22 colunas de dados de município, no arquivo de exemplo utilizado):
`id, nome, microrregiao_id, microrregiao_nome, microrregiao_mesorregiao_id, microrregiao_mesorregiao_nome, microrregiao_mesorregiao_UF_id, microrregiao_mesorregiao_UF_sigla, microrregiao_mesorregiao_UF_nome, microrregiao_mesorregiao_UF_regiao_id, microrregiao_mesorregiao_UF_regiao_sigla, microrregiao_mesorregiao_UF_regiao_nome, regiao_imediata_id, regiao_imediata_nome, regiao_imediata_regiao_intermediaria_id, regiao_imediata_regiao_intermediaria_nome, regiao_imediata_regiao_intermediaria_UF_id, regiao_imediata_regiao_intermediaria_UF_sigla, regiao_imediata_regiao_intermediaria_UF_nome, regiao_imediata_regiao_intermediaria_UF_regiao_id, regiao_imediata_regiao_intermediaria_UF_regiao_sigla, regiao_imediata_regiao_intermediaria_UF_regiao_nome`

(o município de Fernando de Noronha, que não possui `microrregiao`, resulta em uma 23ª coluna `microrregiao` isolada, com valor `NULL` para os demais — comportamento esperado e documentado da descoberta automática).

---

## 2. Estrutura do projeto

```
SpringBatchIbgeMunicipios/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/javaricci/
    │   │   ├── SpringBatchMunicipiosApplication.java   (classe main)
    │   │   ├── config/
    │   │   │   ├── AppProperties.java                  (propriedades de app.* do application.yml)
    │   │   │   ├── DataSourceConfig.java                (DataSource H2 "batch" + SQLite "business")
    │   │   │   ├── DownloadJobConfig.java                (Job 1)
    │   │   │   └── EtlJobConfig.java                     (Job 2, com os 2 Steps)
    │   │   ├── tasklet/
    │   │   │   ├── DownloadMunicipiosTasklet.java        (chama a API, grava o arquivo)
    │   │   │   └── PrepareTableTasklet.java              (descobre colunas, cria tabela)
    │   │   ├── reader/MunicipioJsonItemReader.java       (lê e achata cada município)
    │   │   ├── processor/MunicipioItemProcessor.java     (normaliza/valida)
    │   │   ├── writer/MunicipioItemWriter.java            (INSERT em lote dinâmico)
    │   │   ├── listener/JobCompletionListener.java        (log de início/fim dos Jobs)
    │   │   ├── watcher/MunicipiosFileWatcher.java          (polling -> dispara o Job 2)
    │   │   ├── controller/BatchJobController.java          (REST para disparo manual)
    │   │   ├── runner/StartupRunner.java                   (disparo do Job 1 via --job=download)
    │   │   └── util/JsonFlattenUtil.java                   (flatten do JSON aninhado)
    │   └── resources/application.yml
    └── test/java/com/javaricci/util/JsonFlattenUtilTest.java
```

---

## 3. Pré-requisitos

- **JDK 11**
- **Maven 3.6+**
- Acesso à internet (para baixar dependências do Maven Central na primeira build, e para a API do IBGE em tempo de execução)
- Windows com a unidade `E:` disponível (ou ajuste o caminho — veja seção 5)

---

## 4. Como executar

> **Importante:** execute os comandos abaixo **a partir da pasta raiz do projeto** (`SpringBatchIbgeMunicipios/`, onde está o `pom.xml`), pois é ali que o `SpringBatch.DB` será criado.

```bash
mvn clean package
java -jar target/SpringBatchIbgeMunicipios.jar
```

A aplicação sobe na porta `8080`, cria o diretório `E:\SpringBatch` (se não existir) e o `MunicipiosFileWatcher` fica monitorando continuamente.

### Fluxo completo (download automático + ETL automático)

```bash
# dispara o download já na subida da aplicação
java -jar target/SpringBatchIbgeMunicipios.jar --job=download
```

O `downloadMunicipiosJob` baixa o JSON e grava o arquivo em `E:\SpringBatch`. Assim que a gravação termina, o `MunicipiosFileWatcher` detecta o arquivo estável e dispara automaticamente o `etlMunicipiosJob`, que cria a tabela em `SpringBatch.DB` (na raiz do projeto) e carrega os dados — **sem nenhuma intervenção manual**.

### Disparo manual via REST (alternativa)

Com a aplicação já rodando:

```bash
# 1) baixa o arquivo (o watcher dispara o ETL sozinho em seguida)
curl -X POST http://localhost:8080/api/jobs/download

# 2) ou, se o arquivo já existir em E:\SpringBatch, rodar o ETL diretamente
curl -X POST http://localhost:8080/api/jobs/etl
```

### Simulando manualmente a gravação do arquivo

Você também pode simplesmente **copiar/colar** um arquivo `municipiosbrasil.json` para dentro de `E:\SpringBatch` (por qualquer meio — Explorer, script, outro processo) que o watcher vai detectar e disparar o `etlMunicipiosJob` do mesmo jeito, pois o gatilho é a gravação do arquivo no diretório, e não o Job de download em si.

---

## 5. Configuração (`src/main/resources/application.yml`)

```yaml
app:
  diretorio-monitorado: E:/SpringBatch
  nome-arquivo-json: municipiosbrasil.json
  api-municipios-url: https://servicodados.ibge.gov.br/api/v1/localidades/municipios
  nome-tabela: MunicipiosBrasil
  nome-arquivo-banco: SpringBatch.DB   # sempre gravado na raiz do projeto (user.dir)
  datasource:
    batch:
      url: jdbc:h2:file:E:/SpringBatch/batch-metadata;AUTO_SERVER=TRUE
    business:
      driver-class-name: org.sqlite.JDBC   # URL montada dinamicamente em DataSourceConfig
```

Se for rodar em Linux/macOS para testes (fora do Windows-alvo do enunciado), basta trocar o diretório monitorado, por exemplo:

```yaml
app:
  diretorio-monitorado: /tmp/SpringBatch
  datasource:
    batch:
      url: jdbc:h2:file:/tmp/SpringBatch/batch-metadata;AUTO_SERVER=TRUE
```

(o `SpringBatch.DB` continua indo para a raiz do projeto automaticamente, em qualquer sistema operacional).

Também é possível sobrescrever qualquer propriedade via linha de comando, sem recompilar:

```bash
java -jar target/SpringBatchIbgeMunicipios.jar ^
  --app.diretorio-monitorado=D:/OutroCaminho
```

---

## 6. Consultando o resultado

Com qualquer cliente SQLite (DB Browser for SQLite, DBeaver, extensão do VS Code, etc.), abra o arquivo `SpringBatch.DB` **na raiz do projeto** e consulte:

```sql
SELECT COUNT(*) FROM MunicipiosBrasil;                 -- esperado: 5571
SELECT * FROM MunicipiosBrasil WHERE microrregiao_mesorregiao_UF_sigla = 'SP' LIMIT 10;
```

---

## 7. Decisões técnicas relevantes

- **Spring Boot 2.7.x / Spring Batch 4.3.x**: última linha do Spring Boot 2 com suporte pleno a Java 11 (Spring Boot 3 exige Java 17+).
- **`@EnableBatchProcessing`** (na classe principal): necessária para que o Spring Boot registre `JobRepository`, `JobLauncher`, `JobBuilderFactory` e `StepBuilderFactory`. Sem ela, a aplicação falha ao subir com `NoSuchBeanDefinitionException: JobBuilderFactory`.
- **`spring.batch.job.enabled=false`**: evita que o Spring Boot dispare automaticamente **todos** os `Job` beans a cada subida da aplicação — o disparo é controlado explicitamente (watcher, REST ou `--job=download`).
- **`SpringBatch.DB` resolvido dinamicamente via `System.getProperty("user.dir")`**: garante que o banco de negócio seja sempre criado na raiz do projeto (diretório de trabalho a partir do qual a aplicação é executada), independentemente de onde o arquivo JSON monitorado está configurado (`E:\SpringBatch`).
- **Monitoramento por polling manual, em vez de `java.nio.file.WatchService`**: o `WatchService` nativo do Java no Windows depende da API `ReadDirectoryChangesW`, que não funciona de forma confiável (às vezes não dispara evento nenhum) em unidades virtuais (`subst`), compartilhamentos de rede mapeados como unidade e alguns sistemas de arquivos. O `MunicipiosFileWatcher` verifica periodicamente (`app.watcher.intervalo-poll-ms`) o tamanho e a data de modificação do arquivo — funciona em qualquer tipo de unidade, local ou de rede.
- **Gravação atômica do arquivo baixado** (`Files.write` em `.tmp` + `Files.move`/rename): evita que o watcher veja um arquivo parcialmente escrito.
- **Debounce de estabilidade** no `MunicipiosFileWatcher`: só dispara o Job quando o tamanho do arquivo para de mudar por um intervalo configurável (`app.watcher.estabilidade-ms`), evitando ler o JSON pela metade.
- **Descoberta dinâmica de colunas + `DROP TABLE IF EXISTS` / `CREATE TABLE`** a cada execução do ETL: garante que a tabela sempre reflita fielmente o arquivo mais recente.
- **`@StepScope` com *late binding* via SpEL** (`#{jobExecutionContext['colunasMunicipiosBrasil']}`): é assim que o `Reader` e o `Writer` do segundo Step recebem a lista de colunas calculada dinamicamente pelo primeiro Step, sem acoplamento direto entre as classes.

---

## 8. Testes

```bash
mvn test
```

Inclui teste unitário de `JsonFlattenUtil` (achatamento de JSON aninhado, tratamento de campos nulos, inferência de tipo de coluna).
