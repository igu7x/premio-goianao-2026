--liquibase formatted sql

--changeset goianao:008
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'unidade_judiciaria' AND LOWER(column_name) = 'responsavel_id' AND LOWER(table_schema) = 'public'
-- Superior responsavel pela unidade.
--
-- Ate aqui, quem podia gerenciar a lista de servidores de uma unidade era o
-- magistrado <b>reconhecido</b> nela naquela edicao. Isso amarra duas coisas
-- diferentes: ter vencido o premio e responder pela unidade. Agora o
-- superadministrador designa explicitamente quem responde por cada unidade, e
-- essa designacao vale por unidade -- nao por edicao, porque a chefia da
-- unidade nao muda quando o premio muda de ano.
--
-- Nulo e o estado normal de uma unidade ainda nao designada.
ALTER TABLE unidade_judiciaria ADD COLUMN responsavel_id BIGINT;

ALTER TABLE unidade_judiciaria
    ADD CONSTRAINT fk_unidade_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES usuario (id);
