/*
 * Configuração resolvida em tempo de execução, não de build.
 *
 * O Vite congela `import.meta.env` no momento do build, o que obrigaria a
 * construir uma imagem por ambiente. Aqui a configuração é um arquivo servido
 * ao lado do index.html: a mesma imagem sobe em homologação e em produção, e o
 * script de inicialização do container reescreve este arquivo a partir das
 * variáveis do pod.
 *
 * Vazio significa "mesma origem" — é o que vale em desenvolvimento, onde o
 * proxy do Vite encaminha /api para o backend.
 */
window.__GOIANAO_CONFIG__ = {
  apiBaseUrl: '',
}
