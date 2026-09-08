--liquibase formatted sql

--changeset goianao:006
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'arte_layout' AND LOWER(table_schema) = 'public'
-- Artes dos certificados fora do disco.
--
-- O pod do OpenShift roda sem volume persistente: o que for gravado em disco
-- desaparece no restart. Mais do que isso, a arte faz parte da autenticidade do
-- certificado -- uma reemissao feita daqui a anos tem que sair identica a
-- original, o que so funciona se a arte ainda existir. Guardada aqui, ela cai
-- no mesmo backup da tabela certificado_emitido e as duas nunca se separam.
--
-- Sao 8 artes por edicao (quatro selos x dois tipos), na casa dos poucos MB por
-- edicao. Volume que nao justifica object storage nem a sincronia de dois
-- sistemas de backup.
CREATE TABLE arte_layout (
    referencia  VARCHAR(64)  NOT NULL,
    conteudo    BYTEA        NOT NULL,
    extensao    VARCHAR(10)  NOT NULL,
    tamanho     BIGINT       NOT NULL,
    criado_em   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_arte_layout PRIMARY KEY (referencia)
);
