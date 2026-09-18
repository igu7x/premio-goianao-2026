--liquibase formatted sql

--changeset goianao:012
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'edicao' AND LOWER(column_name) = 'schema_dados' AND LOWER(table_schema) = 'public'
-- Cada edicao passa a ter a propria base (feature 011).
--
-- Este changelog e o do esquema COMPARTILHADO. A partir daqui ele guarda so
-- duas coisas: o catalogo de edicoes e o indice que leva um codigo de
-- certificado ate a edicao onde ele esta. Todo o resto das tabelas passa a
-- existir, repetido e vazio, dentro de `edicao_<ano>` -- criadas pelo changelog
-- de db/edicao/, aplicado schema a schema por BaseDaEdicao.

-- Onde ficam os dados desta edicao. E dado, nao convencao: hoje o nome e
-- derivado do ano (edicao_2026), mas quem le nao deve depender disso. Nulo
-- enquanto a migracao dos dados anteriores nao rodou -- e e justamente o nulo
-- que diz a BaseDaEdicao que ela ainda tem trabalho a fazer.
ALTER TABLE edicao ADD COLUMN schema_dados VARCHAR(63);

-- Indice da verificacao publica.
--
-- A pagina de verificacao e anonima: quem digita um codigo nao tem sessao, e
-- portanto nao tem edicao. Sem este indice, verificar um codigo exigiria varrer
-- o schema de todas as edicoes -- custo que cresce um schema por ano, numa rota
-- exposta a internet e limitada por taxa.
--
-- Guarda o minimo: o codigo e onde procurar. Os dados do certificado continuam
-- morando so na base da edicao.
CREATE TABLE certificado_indice (
    codigo_validacao VARCHAR(32) NOT NULL,
    edicao_id        BIGINT      NOT NULL,
    criado_em        TIMESTAMP   NOT NULL,
    CONSTRAINT pk_certificado_indice        PRIMARY KEY (codigo_validacao),
    CONSTRAINT fk_certificado_indice_edicao FOREIGN KEY (edicao_id) REFERENCES edicao (id)
);

CREATE INDEX ix_certificado_indice_edicao ON certificado_indice (edicao_id);
