--liquibase formatted sql

--changeset goianao:009 context:bootstrap
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM usuario WHERE cpf = '71198182105' OR email = 'teixeiraigor09@gmail.com'
-- Superadministrador inicial.
--
-- Existe porque o sistema tem um problema de partida: so um superadministrador
-- cadastra usuarios, entao sem o primeiro ninguem entra. O caminho normal e
-- SuperadminInicial, que le GOIANAO_SUPERADMIN_* na subida e nao deixa rastro
-- no repositorio. Esta migracao e a alternativa para quando nao se pode mexer
-- nas variaveis do ambiente.
--
-- SOBRE O HASH ESTAR AQUI. Este repositorio e publico. O hash abaixo, portanto,
-- e publico -- e continuara sendo, porque o historico do git nao se apaga. Isso
-- so e aceitavel por dois motivos que precisam continuar valendo:
--
--   1. A senha tem 24 caracteres aleatorios (~146 bits). Nao ha ataque de
--      dicionario nem forca bruta que a alcance, com BCrypt ou sem ele. Uma
--      senha escolhida por pessoa NAO poderia entrar aqui.
--   2. Em producao o login por senha fica desligado (goianao.login.senha),
--      entao esta credencial nem e aceita la. O que a linha faz em producao e
--      util e inofensivo: dar o papel de SUPERADMIN ao CPF que entrar pelo SSO.
--
-- Ainda assim, o certo e trocar a senha no primeiro acesso, pelo proprio modulo
-- de usuarios. Depois disso o hash publicado nao vale para mais nada.
--
-- O context:bootstrap acima mantem a linha fora dos testes, que verificam a
-- contagem do cadastro e falhariam com um usuario a mais. Sem context declarado
-- no ambiente, o Liquibase roda todos os changesets -- entao dev, homologacao e
-- producao recebem esta linha, e so o perfil de teste a dispensa.
--
-- A pre-condicao acima faz a migracao ceder se o usuario ja existir, por CPF ou
-- por e-mail: quem ja criou pelas variaveis de ambiente nao ganha um conflito
-- de chave unica na proxima subida.
INSERT INTO usuario (cpf, nome, email, senha_hash, ativo, criado_em)
VALUES ('71198182105',
        'Igor Freitas Costa Cupertino Teixeira',
        'teixeiraigor09@gmail.com',
        '$2a$10$vkoveE8WY7VhKPaf/q.H9.Enw0z3ljS5l.5LpNxgoTGcmpdqCsaDe',
        TRUE,
        CURRENT_TIMESTAMP);

INSERT INTO usuario_papel (usuario_id, papel)
SELECT id, 'SUPERADMIN' FROM usuario WHERE email = 'teixeiraigor09@gmail.com';

-- Administrador tambem: os papeis acumulam (001/RF-3), e sem ele o
-- superadministrador cadastraria usuarios mas nao configuraria o premio.
INSERT INTO usuario_papel (usuario_id, papel)
SELECT id, 'ADMINISTRADOR' FROM usuario WHERE email = 'teixeiraigor09@gmail.com';
