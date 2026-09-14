--liquibase formatted sql

--changeset goianao:011
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'unidade_judiciaria' AND LOWER(column_name) = 'codigo_siedos' AND LOWER(table_schema) = 'public'
-- Identificadores do SIEDOS, para a integracao com a API corporativa do TJGO
-- (feature 010). A API chaveia unidade por codigo e pessoa por matricula; o
-- Goianao chaveia unidade por nome canonico e pessoa por e-mail (DI-24). Estas
-- colunas sao a ponte entre os dois mundos.
--
-- Todas anulaveis, e nenhuma vira chave: o cadastro atual nasceu do mock e da
-- digitacao, sem codigo nenhum, e continua valido. O codigo e gravado quando a
-- unidade e casada com a API pela primeira vez.
ALTER TABLE unidade_judiciaria ADD COLUMN codigo_siedos BIGINT;
ALTER TABLE unidade_judiciaria ADD COLUMN comarca VARCHAR(150);
ALTER TABLE unidade_judiciaria ADD CONSTRAINT uk_unidade_codigo_siedos UNIQUE (codigo_siedos);

-- De onde a linha veio no RH. Nao identifica ninguem: serve para reencontrar a
-- pessoa na API e para diagnostico.
ALTER TABLE servidor_habilitado ADD COLUMN matricula BIGINT;

ALTER TABLE usuario ADD COLUMN matricula BIGINT;
ALTER TABLE usuario ADD COLUMN login_ad VARCHAR(100);
