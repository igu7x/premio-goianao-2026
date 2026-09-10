--liquibase formatted sql

--changeset goianao:010
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'magistrado_reconhecido' AND LOWER(column_name) = 'email' AND LOWER(table_schema) = 'public'
-- O e-mail corporativo passa a ser a chave do dominio (DI-24).
--
-- O Keycloak do tribunal entrega o e-mail e nao entrega o CPF. Com o CPF como
-- chave, ninguem que entrasse pelo SSO acharia nada seu. A partir daqui quem
-- identifica a pessoa -- no cadastro de usuarios, na lista de magistrados
-- reconhecidos, na de servidores habilitados e no certificado emitido -- e o
-- e-mail. O CPF fica nas tabelas como dado opcional, sem unicidade, so
-- informativo.
--
-- LINHAS ANTERIORES. O e-mail e buscado no cadastro de usuarios pelo CPF. Sem
-- correspondencia, a linha recebe <cpf>@cpf.invalid: o dominio .invalid e
-- reservado (RFC 2606) e nenhum SSO o entrega, entao a linha e preservada,
-- continua ligada aos certificados que ja emitiu (o mesmo CPF gera o mesmo
-- endereco nas quatro tabelas) e ninguem consegue entrar por ela.

-- usuario: o e-mail ja era obrigatorio e unico; o CPF deixa de ser.
ALTER TABLE usuario DROP CONSTRAINT uk_usuario_cpf;
ALTER TABLE usuario ALTER COLUMN cpf DROP NOT NULL;

-- administrador: a chave primaria passa do CPF para o e-mail.
ALTER TABLE administrador ADD COLUMN email VARCHAR(200);
UPDATE administrador
   SET email = (SELECT MAX(u.email) FROM usuario u WHERE u.cpf = administrador.cpf);
UPDATE administrador SET email = cpf || '@cpf.invalid' WHERE email IS NULL;
ALTER TABLE administrador DROP CONSTRAINT pk_administrador;
ALTER TABLE administrador ALTER COLUMN email SET NOT NULL;
ALTER TABLE administrador ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE administrador ADD CONSTRAINT pk_administrador PRIMARY KEY (email);

-- magistrado_reconhecido: um por e-mail em cada edicao.
ALTER TABLE magistrado_reconhecido ADD COLUMN email VARCHAR(200);
UPDATE magistrado_reconhecido
   SET email = (SELECT MAX(u.email) FROM usuario u WHERE u.cpf = magistrado_reconhecido.cpf);
UPDATE magistrado_reconhecido SET email = cpf || '@cpf.invalid' WHERE email IS NULL;
ALTER TABLE magistrado_reconhecido ALTER COLUMN email SET NOT NULL;
ALTER TABLE magistrado_reconhecido ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE magistrado_reconhecido DROP CONSTRAINT uk_magistrado_edicao_cpf;
ALTER TABLE magistrado_reconhecido
    ADD CONSTRAINT uk_magistrado_edicao_email UNIQUE (edicao_id, email);
DROP INDEX ix_magistrado_cpf;
CREATE INDEX ix_magistrado_email ON magistrado_reconhecido (email);

-- servidor_habilitado: um por e-mail em cada edicao x unidade. As colunas de
-- autoria guardavam o CPF de quem fez; agora guardam o e-mail.
ALTER TABLE servidor_habilitado ADD COLUMN email VARCHAR(200);
UPDATE servidor_habilitado
   SET email = (SELECT MAX(u.email) FROM usuario u WHERE u.cpf = servidor_habilitado.cpf);
UPDATE servidor_habilitado SET email = cpf || '@cpf.invalid' WHERE email IS NULL;
ALTER TABLE servidor_habilitado ALTER COLUMN email SET NOT NULL;
ALTER TABLE servidor_habilitado ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE servidor_habilitado DROP CONSTRAINT uk_servidor_habilitado;
ALTER TABLE servidor_habilitado
    ADD CONSTRAINT uk_servidor_habilitado UNIQUE (edicao_id, unidade_id, email);
DROP INDEX ix_servidor_habilitado_cpf;
CREATE INDEX ix_servidor_habilitado_email ON servidor_habilitado (email, edicao_id);
ALTER TABLE servidor_habilitado ALTER COLUMN criado_por SET DATA TYPE VARCHAR(200);
ALTER TABLE servidor_habilitado ALTER COLUMN atualizado_por SET DATA TYPE VARCHAR(200);

-- certificado_emitido: o certificado logico passa a ser um por
-- (edicao, tipo, e-mail, unidade). O codigo de validacao nao muda -- quem ja
-- conferiu um certificado impresso continua achando o mesmo registro.
ALTER TABLE certificado_emitido ADD COLUMN email_emissor VARCHAR(200);
UPDATE certificado_emitido
   SET email_emissor = (SELECT MAX(u.email) FROM usuario u
                         WHERE u.cpf = certificado_emitido.cpf_emissor);
UPDATE certificado_emitido
   SET email_emissor = cpf_emissor || '@cpf.invalid' WHERE email_emissor IS NULL;
ALTER TABLE certificado_emitido ALTER COLUMN email_emissor SET NOT NULL;
ALTER TABLE certificado_emitido ALTER COLUMN cpf_emissor DROP NOT NULL;
ALTER TABLE certificado_emitido DROP CONSTRAINT uk_certificado_logico;
ALTER TABLE certificado_emitido
    ADD CONSTRAINT uk_certificado_logico UNIQUE (edicao_id, tipo, email_emissor, unidade_id);
DROP INDEX ix_certificado_emissor;
CREATE INDEX ix_certificado_emissor ON certificado_emitido (email_emissor, edicao_id);
