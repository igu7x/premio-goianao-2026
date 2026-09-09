/*
 * Servidor estático do frontend em produção.
 *
 * Por que existe: a pipeline do tribunal executa `npm run <script>` numa imagem
 * Node e expõe a porta 8080. Sem isto ela acabava rodando `npm run dev` — o
 * servidor de desenvolvimento do Vite, que escuta em localhost:5173, não é
 * alcançável de fora do container e serve o código sem compilar. O pod fica
 * "Running" e a Route devolve 503, que foi exatamente o que aconteceu.
 *
 * Sem dependência alguma, só o que vem no Node. Assim funciona mesmo quando a
 * instalação de produção descarta as devDependencies (o Vite é uma delas), e
 * não acrescenta nada para auditar em `npm audit`.
 */
import { createServer } from 'node:http'
import { createReadStream } from 'node:fs'
import { stat, writeFile } from 'node:fs/promises'
import { extname, join, normalize, resolve } from 'node:path'

const RAIZ = resolve(import.meta.dirname, 'dist')
const PORTA = Number(process.env.PORT ?? 8080)

const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
}

/*
 * A base da API é resolvida em tempo de execução, não de build: o Vite
 * congelaria o valor no bundle e seria preciso uma imagem por ambiente. Este
 * arquivo é reescrito a cada subida, então a mesma imagem de homologação pode
 * ser promovida para produção.
 *
 * Vazio significa "mesma origem" — o que vale em desenvolvimento, onde o proxy
 * do Vite encaminha /api para o backend.
 */
async function escreverConfig() {
  const base = (process.env.API_BASE_URL ?? '').replace(/\/+$/, '')
  await writeFile(
    join(RAIZ, 'config.js'),
    `window.__GOIANAO_CONFIG__ = {\n  apiBaseUrl: ${JSON.stringify(base)},\n}\n`,
    'utf8',
  )
  console.log(`[goianao] base da API: ${base || '(mesma origem)'}`)
}

/**
 * Resolve o caminho pedido dentro de dist/, recusando qualquer coisa que escape
 * da raiz. `normalize` sozinho não basta: sem a checagem do prefixo, um pedido
 * com `..` sairia da pasta servida.
 */
function caminhoSeguro(url) {
  const semQuery = decodeURIComponent(url.split('?')[0].split('#')[0])
  const destino = resolve(join(RAIZ, normalize(semQuery)))
  return destino === RAIZ || destino.startsWith(RAIZ + '/') || destino.startsWith(RAIZ + '\\')
    ? destino
    : null
}

/*
 * O bundle tem hash no nome, então pode ser guardado por muito tempo. O
 * index.html e o config.js, não: são os dois arquivos que mudam de conteúdo
 * mantendo o nome, e um cache agressivo neles serve versão velha depois do
 * deploy.
 */
function cache(caminho) {
  return /(index\.html|config\.js)$/.test(caminho)
    ? 'no-cache, no-store, must-revalidate'
    : 'public, max-age=31536000, immutable'
}

function servir(resposta, caminho, status = 200) {
  resposta.writeHead(status, {
    'Content-Type': TIPOS[extname(caminho).toLowerCase()] ?? 'application/octet-stream',
    'Cache-Control': cache(caminho),
    'X-Content-Type-Options': 'nosniff',
  })
  createReadStream(caminho).pipe(resposta)
}

const servidor = createServer(async (requisicao, resposta) => {
  if (requisicao.method !== 'GET' && requisicao.method !== 'HEAD') {
    resposta.writeHead(405, { Allow: 'GET, HEAD' }).end()
    return
  }

  // Sonda de saúde: responde sem tocar no disco, para a probe não depender de
  // leitura de arquivo nem aparecer no log de acesso como 404.
  if (requisicao.url === '/saude') {
    resposta.writeHead(200, { 'Content-Type': 'text/plain' }).end('ok')
    return
  }

  const caminho = caminhoSeguro(requisicao.url ?? '/')
  if (!caminho) {
    resposta.writeHead(403).end()
    return
  }

  try {
    const info = await stat(caminho)
    if (info.isFile()) {
      servir(resposta, caminho)
      return
    }
  } catch {
    /* não existe: cai no index.html abaixo */
  }

  /*
   * Rota de aplicação de página única. O React Router resolve
   * /verificar/ABCD-1234-EFGH no navegador, mas quem abre esse endereço direto
   * — e é o que acontece com o QR do certificado — faz uma requisição real ao
   * servidor, que não tem esse arquivo. Sem esta devolução do index.html, ler o
   * QR de um certificado daria 404.
   */
  servir(resposta, join(RAIZ, 'index.html'))
})

try {
  await stat(join(RAIZ, 'index.html'))
} catch {
  console.error(
    `[goianao] ${RAIZ}/index.html não existe. A etapa de build precisa rodar` +
      ' `npm run build` antes de `npm start`.',
  )
  process.exit(1)
}

await escreverConfig()

// 0.0.0.0, e não localhost: dentro de um container, ouvir só a interface local
// torna o processo inalcançável pelo Service, e o sintoma é um pod "Running"
// com a Route devolvendo 503.
servidor.listen(PORTA, '0.0.0.0', () => {
  console.log(`[goianao] servindo ${RAIZ} em http://0.0.0.0:${PORTA}`)
})

// O OpenShift envia SIGTERM ao encerrar o pod; sem isto o processo só morre no
// SIGKILL, 30 segundos depois, e cada deploy fica mais lento do que precisa.
for (const sinal of ['SIGTERM', 'SIGINT']) {
  process.on(sinal, () => servidor.close(() => process.exit(0)))
}
