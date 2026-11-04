# Urbaniza

Aplicação para avaliar segurança, mobilidade e iluminação de regiões de São Paulo. Frontend HTML/CSS/JavaScript com Fetch API e backend Java 21 + Spring Boot 3.5.7, Spring Data JPA, Bean Validation e Spring Security com JWT.

## Executar

Requisitos: **JDK 21 completo** (incluindo `javac`). O script `mvnw` para Linux usa Maven instalado ou baixa Maven 3.9.9 verificando seu SHA-512; requer Bash, Python 3, curl, tar e sha512sum nesse último caso. No ambiente da nuvem, as ferramentas já foram preparadas. Em outras máquinas também é possível usar Maven 3.9+ diretamente (`mvn` no lugar de `./mvnw`).

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

O Spring Boot serve **as telas e a API na mesma origem**, porta 8080. A entrada `/` redireciona para `/Index.html` (I maiúsculo). Não use o servidor HTTP do Python nem abra os HTMLs por `file://`: os formulários precisam da API. Alterações nos HTML/CSS/JS da raiz são copiadas para `target/classes/static` pela fase de recursos do Maven; reinicie `spring-boot:run` após alterá-las.

Para gerar e executar o aplicativo empacotado:

```bash
./mvnw package
java -jar target/urbaniza-1.0.0.jar
```

O perfil padrão é `dev`. Os dados ficam em `.urbaniza/db.mv.db`, e a chave JWT de desenvolvimento é gerada em `.urbaniza/jwt.key` com permissão restrita. Os dados sobrevivem a reinícios. Essa pasta é ignorada pelo Git; não a apague para atualizar o projeto. O catálogo de 96 distritos e 517 nomes locais de bairros é importado automaticamente. Registros e avaliações anteriores são preservados; bairros homônimos em distritos diferentes têm IDs próprios. Não há usuários, senhas ou avaliações fictícias cadastrados por padrão. As regiões sem avaliações mostram **Sem notas**.

## Windows e IntelliJ IDEA

1. Atualize o projeto com `git pull origin main` na pasta local do repositório.
2. No IntelliJ, abra o `pom.xml` como projeto Maven e aguarde a importação das dependências.
3. Em **File → Project Structure → Project SDK**, selecione um **JDK 21 completo**. Configure também o JDK 21 nas opções de execução do Maven, se necessário.
4. Execute a classe `src/main/java/br/com/urbaniza/UrbanizaApplication.java` pelo botão verde ao lado de `main`. Use a raiz do projeto como diretório de trabalho.
5. Abra `http://localhost:8080` no navegador. Para parar o servidor, use **Stop** no IntelliJ.

O IntelliJ pode usar seu Maven integrado; não é necessário executar o script Bash `mvnw` no PowerShell. Para executar no terminal do Windows com Maven instalado no PATH:

```powershell
cd D:\Download\Urbaniza
mvn verify
mvn spring-boot:run
```

O caminho `/workspace/Urbaniza` e `scripts/setup-cloud.sh` pertencem ao ambiente Linux na nuvem, não à sua máquina Windows. O banco H2 funciona também no Windows. Guarde a pasta `.urbaniza` no seu diretório de usuário e mantenha suas permissões de acesso restritas, pois ela contém o banco e a chave JWT local.

## Docker e teste no Render

O `Dockerfile` compila com Maven/JDK 21 e executa o jar com JRE 21, incluindo frontend e backend na mesma imagem. O processo roda sem usuário root; `.dockerignore` exclui banco local, chave JWT, caches e arquivos `.env` do contexto de build.

```bash
docker build -t urbaniza .
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev urbaniza
```

No Render, crie um **Web Service**, conecte este repositório, escolha **Docker** e configure `SPRING_PROFILES_ACTIVE=dev` para demonstração. O aplicativo lê `PORT` automaticamente. Esse modo usa H2 em arquivo e mostra códigos de recuperação na tela: use dados fictícios. Sem disco persistente, banco e chave JWT podem ser perdidos em reinícios/redeploys. Para persistência externa, use o perfil `prod` com PostgreSQL e as variáveis descritas abaixo.

## Funcionalidades

- Cadastro com nome, username exclusivo, email exclusivo e senha de 8 a 72 caracteres (limite adicional de 72 bytes UTF-8 do BCrypt).
- Login por username ou email; mostrar/ocultar senha; JWT com validade de duas horas, mantido em `sessionStorage` e enviado em `Authorization: Bearer` pelo Fetch.
- Perfil com o cartão, avatar e seção Suporte do frontend original. Clique no cartão para editar nome, username e localidade em um diálogo. Inclui logout com revogação de sessões e exclusão da conta mediante confirmação de senha. Email é a identidade de recuperação e não é editado nesse formulário.
- Recuperação em três etapas: solicitar código, verificar código e definir nova senha. Código válido por dez minutos, até cinco tentativas e intervalo mínimo de um minuto entre envios. Após validar o código, o token aleatório permite uma única redefinição por até dez minutos. Alterar a senha revoga os JWTs anteriores.
- **Modo local escolhido para este projeto:** o código de recuperação aparece na tela, com aviso explícito de que nenhum email foi enviado. Exclusivo para desenvolvimento. Não representa verificação real de posse do email.
- Avaliações com notas de 1 a 5 em cada categoria e comentário de até mil caracteres. Uma avaliação por usuário/região; criar, listar, filtrar por região ou pelas próprias avaliações, editar e excluir. O formulário permite pesquisar as regiões cadastradas pelo nome sem diferenciar acentos. O catálogo inclui localidades conhecidas pelos moradores, como Chácara Santo Antônio, associada ao distrito de Santo Amaro, mas com avaliação própria. O distrito aparece nas opções para distinguir nomes iguais.
- Médias calculadas com as avaliações persistidas; início mostra dois destaques entre as regiões avaliadas: maior média e menor média. Sem avaliações, exibe uma mensagem; com somente uma região avaliada, mostra um único cartão até haver outra para comparar.
- Mapa interativo Leaflet com o visual original: ruas OpenStreetMap, limites GeoJSON de São Paulo com destaque ao passar o mouse, marcadores nomeados, card flutuante com estrelas, busca, categorias, zoom e localização. A visão inicial mostra os distritos; pesquisar um nome também encontra os bairros locais. As notas e quantidades vêm da API; não há médias fictícias. O GeoJSON original é carregado do Código Urbano, com cópia local como alternativa em falhas de conexão. As ruas exigem internet e acesso a `tile.openstreetmap.org`; os limites e avaliações continuam disponíveis sem essa camada. Geolocalização depende da permissão do navegador e de HTTPS ou localhost.
- Administração com listagem e consulta por username, alteração de papéis e exclusão de usuários. Administradores podem editar/excluir avaliações de outros usuários; usuários comuns só podem editar/excluir suas próprias avaliações. O último administrador não pode ser excluído nem rebaixado.

## Primeiro administrador e RBAC

Cadastros públicos sempre recebem `USER`; um cliente não pode escolher seu papel. Para criar o primeiro `ADMIN`, configure **antes de iniciar**:

| Variável | Uso |
| --- | --- |
| `ADMIN_EMAIL` | Email exclusivo da nova conta administrativa |
| `ADMIN_USERNAME` | Username exclusivo (padrão `admin`) |
| `ADMIN_PASSWORD` | Senha forte com pelo menos 8 caracteres |

Defina os valores no ambiente do processo ou nas configurações seguras da sua plataforma, nunca no código ou em commits. O bootstrap cria uma **nova** conta apenas quando não há administradores; não promove automaticamente contas existentes com o mesmo email/username. Remova a senha de bootstrap do ambiente depois de criar a conta. O link **Administrar usuários** aparece no perfil de quem possui `ADMIN`. Outros administradores podem ser promovidos pelo painel. Mudanças de papel invalidam os tokens do usuário afetado.

## Etapas das aulas

| Etapa | Implementação |
| --- | --- |
| 01 — GET, listagem e username | `UsuarioController`: listagem administrativa e consulta por username; `AvaliacaoController`: regiões e avaliações |
| 02 — POST e validação | DTOs em `Dtos.java` com `@Valid`, `@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@Min` e `@Max`; erros JSON padronizados |
| 03 — PUT, DELETE e Fetch | Atualização de perfil, avaliações e papéis; exclusão de conta/avaliações; `App.js` e `Script_mapa.js` |
| 04 — Entities | `Usuario`, `Regiao`, `Avaliacao` e `RecuperacaoSenha`; relações `@ManyToOne`, IDs, unicidade e datas |
| 05 — Repositories e JPA | Interfaces `JpaRepository`, serviços transacionais e H2 persistente; driver PostgreSQL disponível |
| 06 — Login e JWT | Senhas BCrypt, emissão/validação HS256 pelo Spring Security OAuth2 Resource Server |
| 07 — JWT e RBAC | API autenticada, regras `USER`/`ADMIN`, autorização por proprietário, validação da versão do token no banco e integração Fetch |

## API

Todas as rotas abaixo começam com `/api`. Exceto as rotas públicas de autenticação, envie o JWT no cabeçalho `Authorization: Bearer <token>`.

| Método e rota | Acesso e finalidade |
| --- | --- |
| `POST /auth/cadastro` | Público; `{nome, username, email, senha}` → sessão e usuário, HTTP 201 |
| `POST /auth/login` | Público; `{identificador, senha}` → `{token, expiraEm, usuario}` |
| `POST /auth/logout` | Autenticado; invalida sessões do usuário, HTTP 204 |
| `POST /auth/recuperar` | Público; `{email}` → mensagem genérica; em dev inclui `codigoDesenvolvimento` no primeiro pedido |
| `POST /auth/verificar` | Público; `{email, codigo}` → token de uso único |
| `POST /auth/nova-senha` | Público; `{token, senha}` → HTTP 204 |
| `GET /usuarios/me` | Perfil atual, sem senha/hash |
| `GET /usuarios/{username}` | Próprio perfil ou consulta por administrador |
| `PUT /usuarios/me` | `{nome, username, localidade}` |
| `DELETE /usuarios/me` | `{senha}` → HTTP 204; remove também avaliações e recuperação de senha |
| `GET /regioes?busca=...` | Regiões, coordenadas, quantidade e médias por categoria |
| `GET /avaliacoes?regiaoId=...&minhas=true` | Avaliações; filtros opcionais |
| `POST /avaliacoes` | `{regiaoId, seguranca, mobilidade, iluminacao, comentario}` → HTTP 201 |
| `PUT /avaliacoes/{id}` | Mesmo corpo; proprietário ou ADMIN; a região não muda |
| `DELETE /avaliacoes/{id}` | Proprietário ou ADMIN → HTTP 204 |
| `GET /admin/usuarios` | ADMIN; lista usuários |
| `PUT /admin/usuarios/{id}/papel` | ADMIN; `{papel: "USER"}` ou `{papel: "ADMIN"}` |
| `DELETE /admin/usuarios/{id}` | ADMIN; remove usuário e dados associados |

Erros usam JSON com `message` e, para validação, `fields`. Status: 400 dados inválidos, 401 sessão/credenciais inválidas, 403 sem permissão, 404 recurso inexistente, 409 duplicidade ou conflito. Senhas, hashes e códigos persistidos nunca são serializados em respostas de perfil.

Exemplo de criação de avaliação via Fetch (o mesmo padrão está em `App.js`):

```javascript
const resposta = await fetch('/api/avaliacoes', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${sessionStorage.getItem('urbaniza.token')}`
  },
  body: JSON.stringify({
    regiaoId: 'pinheiros', seguranca: 4, mobilidade: 5,
    iluminacao: 4, comentario: 'Boa mobilidade.'
  })
});
const dados = await resposta.json();
if (!resposta.ok) throw new Error(dados.message);
```

## PostgreSQL e envio de email

O fluxo local completo não depende dessas integrações. Para usar um PostgreSQL provisionado e enviar códigos reais por SMTP, inicie com `SPRING_PROFILES_ACTIVE=prod` e configure:

| Variável | Descrição |
| --- | --- |
| `DATABASE_URL` | URL JDBC, por exemplo `jdbc:postgresql://servidor:5432/urbaniza` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Credenciais do banco |
| `JWT_SECRET` | Chave **aleatória** em base64 com pelo menos 32 bytes; obrigatória fora de dev/test |
| `SMTP_HOST`, `SMTP_PORT` | Servidor de email; porta padrão 587 |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Credenciais SMTP |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Padrão `true`; ajustar conforme o provedor |
| `MAIL_FROM` | Remetente autorizado no provedor |
| `PORT` | Porta HTTP opcional (padrão 8080) |

O perfil `prod` não expõe códigos de recuperação. Não ative `app.reset.dev-mode` em ambiente publicado. Banco e SMTP precisam estar acessíveis pela rede da plataforma. As credenciais de JDBC/SMTP são consumidas pelo processo; não use placeholders de proxy HTTPS como se fossem senhas locais. A implantação real com PostgreSQL/SMTP não foi validada sem esses serviços.

O perfil de produção usa `ddl-auto=update` para facilitar o exercício; para implantação real, adote migrações versionadas, TLS, backup e proteção contra abuso de login. O projeto é uma implementação funcional para o conteúdo das aulas, não uma implantação de produção já provisionada.

## Testes

```bash
./mvnw verify
```

A suíte `UrbanizaIntegrationTest` usa banco H2 em memória isolado: cadastro/login por username e email, validação, tentativa de injetar ADMIN, JWT ausente/inválido/adulterado/expirado, privacidade de perfil, CRUD, médias, unicidade, proprietário, RBAC, proteção do último admin, revogação, exclusão em cascata, recuperação de uso único, expiração e limite de tentativas, além das páginas estáticas.

Há também um teste real de navegador em `scripts/test-ui.py` (Python Playwright + Chromium). **Execute-o somente em banco de testes**, com um servidor já iniciado e modo de recuperação local. Cria contas descartáveis e as remove ao terminar com sucesso:

```bash
UI_BASE_URL=http://127.0.0.1:8081 python3 scripts/test-ui.py
```

Opcionalmente defina `UI_ADMIN_USERNAME` e `UI_ADMIN_PASSWORD` para incluir os fluxos administrativos; sem essas variáveis, essa parte é registrada como não executada. A suíte validada durante a implementação incluiu também administração. O teste de navegador não faz parte do ciclo Maven e não instala ferramentas de navegador automaticamente.

## Estrutura

- HTML/CSS/JS e `assets/` na raiz: mantém o frontend original acessível e empacota somente os recursos definidos no `pom.xml`.
- `src/main/java/br/com/urbaniza/model`: entidades JPA.
- `repository`: acesso a dados.
- `service`: regras de negócio, autenticação e recuperação.
- `web`: controladores REST, DTOs e tratamento de erros.
- `config`: Spring Security e carga idempotente das regiões.
- `src/main/resources`: perfis de configuração sem segredos.
- `.urbaniza/`: banco, chave JWT e cache local do Maven; ignorados pelo Git.

Fontes, metodologia, cobertura e licença ODbL dos nomes locais estão em `assets/FONTES.md`. O número 472 é uma estimativa citada no projeto, não uma lista oficial adotada pelo aplicativo: a fonte consultada forneceu 517 localidades além dos 96 distritos.
