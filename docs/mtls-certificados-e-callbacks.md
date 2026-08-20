# mTLS, certificados e callbacks — iotcertifier

Guia operacional para conectar o gateway ao `iotcertifier.exati.com.br:8443`.

---

## 1. Arquitetura: são duas direções de mTLS

TALQ é bidirecional — o gateway é **cliente e servidor** ao mesmo tempo (§3.2, p.16). A tela de Certificados do dashboard cobre as duas pontas.

### Direção 1 — nosso gateway → certifier (porta 8443)

Nós somos o cliente. Precisamos de **certificado + chave privada**. Duas opções na tela:

- `Cert Cliente Gateway` / *Generic Product Certificate* — certificado genérico de teste da EXATI
- Seção **`Certificados CA do Vendor`** — subimos nossa própria CA e emitimos os leaf certs

### Direção 2 — certifier → nosso gateway (callbacks no `gatewayUri`)

Eles são o cliente. Precisamos:

- Confiar no `Cert. Cliente da Plataforma` (CN `iotcertifier.exati.com.br`) que **eles** apresentam
- Baixar a `CA Raiz` / *Vendor Certifier CA* para o **nosso truststore**

### O que foi confirmado no handshake

```
subject = C=BR, ST=Parana, L=Curitiba, O=Exati, OU=IoT, CN=iotcertifier.exati.com.br
issuer  = C=BR, ST=Parana, L=Curitiba, O=Exati, OU=IoT, CN=Vendor Certifier CA
notAfter = Apr  6 18:01:10 2036 GMT          (bate com o "06/04/2036" do dashboard)
SAN = DNS:iotcertifier.exati.com.br
EKU = TLS Web Server Authentication
"No client certificate CA names sent"
```

Ou seja: o `Servidor TLS` da tela é o certificado servido na 8443, emitido pela `Vendor Certifier CA`. E o servidor **não pede** certificado de cliente no handshake TLS — a exigência é aplicada na **camada de aplicação**, o que explica o `403` com mensagem em vez de falha de TLS:

```json
{"detail":"No client certificate was presented. Ensure your gateway sends its client certificate on port 8443."}
```

### Detalhe decisivo

Todas as linhas de **chave privada** (`Chave do Cliente Gateway`, `Chave Privada`, `Chave Privada CA`) têm o botão "Baixar" **desabilitado** — só os certificados baixam. Sem a chave privada, o *Generic Product Certificate* não é utilizável para mTLS. Isso indica que o caminho pretendido é **subirmos nossa própria CA**.

---

## 2. Regras para criar os certificados

### Antes: dois ajustes de ambiente obrigatórios no Git Bash (Windows)

Testado nesta máquina — sem os dois, **nada funciona**:

```bash
# 1) O OPENSSL_CONF do sistema aponta para uma instalação do PostgreSQL
#    que não existe. Precisa apontar para um .cnf válido, em caminho WINDOWS.
export OPENSSL_CONF="$(cygpath -w /usr/ssl/openssl.cnf)"

# 2) O Git Bash converte "/C=BR/O=..." em caminho de arquivo e corrompe o -subj.
export MSYS_NO_PATHCONV=1
```

Sintomas se esquecer: `BIO_new_file:no such file` (falta o 1) ou
`subject name is expected to be in the format /type0=value0...` com um caminho
`C:/Users/.../Git/C=BR/...` no meio (falta o 2).

### Geração (sequência verificada, gera `verify: OK`)

```bash
# 1. CA própria — é só o .crt dela que sobe no dashboard
openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
  -keyout nansen-ca.key -out nansen-ca.crt \
  -subj "/C=BR/O=Nansen/OU=IoT/CN=Nansen Gateway CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

# 2. Chave + CSR do gateway
openssl req -newkey rsa:2048 -sha256 -nodes \
  -keyout gw.key -out gw.csr \
  -subj "/C=BR/O=Nansen/OU=IoT/CN=gateway-01.nansen.com.br"

# 3. Assinar o leaf com a nossa CA
#    ATENCAO: use ARQUIVO de extensoes, nao process substitution <(...).
#    O openssl nativo do Windows nao abre /dev/fd/NN.
printf "basicConstraints=CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=clientAuth,serverAuth\nsubjectAltName=DNS:gateway-01.nansen.com.br\n" > gw.ext

openssl x509 -req -in gw.csr -CA nansen-ca.crt -CAkey nansen-ca.key \
  -CAcreateserial -out gw.crt -days 825 -sha256 -extfile gw.ext

# 4. Conferir
openssl verify -CAfile nansen-ca.crt gw.crt
openssl x509 -in gw.crt -noout -subject -issuer \
  -ext subjectAltName,extendedKeyUsage,basicConstraints
```

Saída esperada da conferência:

```
gw.crt: OK
subject=C=BR, O=Nansen, OU=IoT, CN=gateway-01.nansen.com.br
issuer =C=BR, O=Nansen, OU=IoT, CN=Nansen Gateway CA
X509v3 Basic Constraints:        CA:FALSE
X509v3 Extended Key Usage:       TLS Web Client Authentication, TLS Web Server Authentication
X509v3 Subject Alternative Name: DNS:gateway-01.nansen.com.br
```

### O que importa

- **`extendedKeyUsage=clientAuth`** é obrigatório para mTLS. Incluímos **`serverAuth`** junto porque o mesmo gateway precisa servir HTTPS para os callbacks do CMS. Se usarmos certificados separados para cada ponta, aí sim dá para separar as EKU.
- **`subjectAltName` deve casar com o host do `gatewayUri`** anunciado no bootstrap. Validador moderno ignora o CN e olha só o SAN.
- **RSA 2048+ ou EC P-256**, assinatura SHA-256. Nada de SHA-1.
- **Subir apenas `nansen-ca.crt`** no campo "PEM DO CERTIFICADO CA". A `nansen-ca.key` nunca sai da nossa infra.
- Validade do leaf **≤ 825 dias** é a convenção segura.

### Por que o CN é um nome na CA e um hostname no leaf

Não é inconsistência — são dois papéis diferentes:

| | CN da CA | CN/SAN do leaf |
|---|---|---|
| Valor | `Nansen Gateway CA` | `gateway-01.nansen.com.br` |
| Função | rótulo humano do emissor | identidade de rede verificável |
| É resolvido em DNS? | **não, nunca** | sim |
| Validado contra o quê? | nada — só aparece como `issuer` na cadeia | o host da conexão TLS |

A CA nunca é alvo de conexão, então o nome dela é livre — serve para você reconhecer quem assinou. O leaf **é** alvo de conexão: quando o CMS chama `https://<gatewayUri>/devices`, o cliente TLS compara o host da URL com o `subjectAltName` do certificado. Se não bater, a conexão falha antes de qualquer requisição.

Por isso o CN da CA pode ser uma frase e o do gateway tem que ser exatamente o hostname que anunciamos no `gatewayUri`.

---

## 3. Como aplicar nas rotas

**Ordem obrigatória:** subir `nansen-ca.crt` no dashboard ("Certificados CA do Vendor") **antes** de testar. Sem isso o load balancer rejeita — comprovado, ver evidência abaixo.

```bash
# baixar do dashboard: CA Raiz (Vendor Certifier CA) -> vendor-certifier-ca.crt
B="https://iotcertifier.exati.com.br:8443/cms/<token>"

curl -i --cert gw.crt --key gw.key --cacert vendor-certifier-ca.crt \
  -H "talq-api-version: 2.6.0" \
  "$B/devices?clientAddress=00000000-0000-0000-0000-000000000000"
```

O `--cacert` substitui o `-k`: com a CA Raiz deles no truststore, o certificado auto-assinado passa a validar de verdade. O `--cert/--key` é o que deve eliminar o `403`.

### ARMADILHA: o curl do Git Bash (Windows) NÃO faz mTLS com arquivo PEM

O curl que vem no Git para Windows é compilado com backend **Schannel**
(`curl -V` mostra "Schannel"), que não aceita certificado de cliente vindo de
arquivo. Sintomas: exit code **58** com `--cert x.crt --key x.key`, ou exit
**0** com HTTP code **000** usando `--cert x.p12 --cert-type P12`. Converter
para PKCS#12 **não** resolve.

Alternativas:

1. **Testar com `openssl s_client`** (funciona, foi como validamos):
   ```bash
   printf 'GET /cms/<token>/groups?clientAddress=00000000-0000-0000-0000-000000000000 HTTP/1.1\r\nHost: iotcertifier.exati.com.br:8443\r\ntalq-api-version: 2.6.0\r\nConnection: close\r\n\r\n' \
   | openssl s_client -connect iotcertifier.exati.com.br:8443 \
       -servername iotcertifier.exati.com.br \
       -cert gw.crt -key gw.key -quiet
   ```
2. Usar um curl com backend OpenSSL (WSL, ou o curl do Windows em
   `C:\Windows\System32\curl.exe` — verificar o backend antes).
3. Importar o `.p12` no repositório de certificados do Windows e deixar o
   Schannel selecioná-lo.

Isso é limitação **da ferramenta de teste**, não do gateway. Java/Python/Node
usam seus próprios stacks TLS e não sofrem disso.

### Evidência: as três respostas do certifier

Progressão observada, que serve de checklist de diagnóstico:

| Situação | Resposta |
|---|---|
| Sem certificado de cliente | `403` — `"No client certificate was presented. Ensure your gateway sends its client certificate on port 8443."` |
| Com certificado, **CA não registrada** | `"Client certificate rejected by the load balancer: client_cert_validation_failed. Subject: unknown, Issuer: unknown. Verify that your CA certificate has been registered with the platform admin."` |
| Com certificado, CA registrada **em Certificados de Produto** | **mesma rejeição** |
| Com certificado, CA registrada **em Produto + Vendor** | **mesma rejeição** |

### BLOQUEIO CONFIRMADO: o cadastro self-service não alimenta o load balancer

Testado em 2026-08-12 com a CA `Nansen TALQ Gateway CA v2`
(SHA-256 `21:C1:C3:64:15:48:41:92:CD:0E:E8:38:F5:42:63:AB:54:D1:B3:68:EF:48:4F:5E:B3:A7:17:68:12:A2:CE:49`).

Descartado do nosso lado — tudo verificado como correto:

| Verificação | Resultado |
|---|---|
| Certificado é transmitido no handshake | OK — servidor envia `CertificateRequest`, cliente responde `Certificate` + `CertificateVerify` |
| Certificado válido e encadeando na CA | OK — `openssl verify: gw.crt: OK` |
| Cadastro persiste no dashboard após refresh | OK — confirmado visualmente |
| Espera por propagação (retest minutos depois) | falha idêntica |
| Envio da cadeia completa (`-cert_chain`) | falha idêntica |
| CA registrada em **ambos** os escopos | falha idêntica |

Conclusão: cadastrar a CA pela interface **não** insere a CA no trust store do
balanceador. A própria mensagem aponta o caminho — *"registered with the
**platform admin**"*. Existe uma etapa administrativa do lado da EXATI que não
é self-service.

Enquanto isso não for feito, **nenhuma rota do certifier é alcançável**.

A segunda mensagem confirma a arquitetura: o mTLS é terminado num **load
balancer do Google Cloud** (headers `server: Google Frontend`, `via: 1.1
google`), que valida o certificado contra as CAs registradas no dashboard antes
de repassar à aplicação. Por isso o handshake TLS aceita qualquer certificado
(`No client certificate CA names sent`) e a rejeição vem como resposta HTTP.

No gateway real: **keystore** com `gw.crt` + `gw.key`, **truststore** com `vendor-certifier-ca.crt`.

Lembrando que a rota no certifier **não tem `/talq`** — a base já embute o mount (`/cms/<token>/`). O prefixo `/talq` é do ambiente `staging`, que é outro sistema.

---

## 4. Validação de "is alive" no nosso webhook: não existe na spec

Pesquisado em `talq.txt` (64 das 66 páginas da spec 2.6.3 — a ausência é real, não falha de extração). **Zero ocorrências** de `alive`, `heartbeat`, `keepalive`, `health`, `liveness`, `ping` ou `watchdog`.

O TALQ **não define nenhum mecanismo de health check** do CMS contra o `gatewayUri`. O que existe de mais próximo:

| Mecanismo | O que é | Onde |
|---|---|---|
| `communicationFailure` | **evento** da Basic function, por dispositivo, reportado via log report | §4.2 |
| Resync flows | CMS apaga o gateway-como-device → gateway reinicia bootstrap; e o inverso | §5.1, p.44 |
| HTTP keep-alive | reuso de conexão TCP, motivo declarado para exigir HTTP/1.1 | §3.5 |

O keep-alive é **transporte**, não liveness de aplicação. E o `communicationFailure` é do gateway reportando dispositivo inacessível na ODN — não do CMS testando o gateway.

**Em aberto:** se o dashboard do certifier faz alguma sonda de alcançabilidade no `gatewayUri` antes de liberar o bootstrap, isso é comportamento próprio da EXATI e não está documentado. Vale perguntar.

---

## 5. Rotas que o CMS vai chamar no nosso gateway

Todas em `https://<gatewayUri>/<rota>?clientAddress=<cmsAddress>`, conforme `talq-api-gateway-2-6-3-online.json` — **44 paths**. É a superfície que precisamos implementar como servidor.

### Dispositivos e classes
```
GET,PATCH,POST,PUT   /devices
GET                  /devices/count
DELETE,GET,PATCH     /devices/{deviceAddress}
GET,PATCH            /devices/{deviceAddress}/{functionId}
GET,PATCH            /devices/{deviceAddress}/{functionId}/{attributeName}
GET                  /device-classes
GET                  /device-classes/count
GET                  /device-classes/{className}
```

### Controle e override
```
POST                 /override-commands
GET,POST             /assign-commands
GET,POST,PUT         /control-programs
GET                  /control-programs/count
DELETE,GET,PUT       /control-programs/{controlProgramAddress}
GET,POST,PUT         /calendars
GET                  /calendars/count
DELETE,GET,PUT       /calendars/{calendarAddress}
```

### Grupos
```
GET,POST,PUT         /groups
GET                  /groups/count
DELETE,GET,PUT       /groups/{groupAddress}
PUT                  /groups/{groupAddress}/members
GET                  /groups/{groupAddress}/members/count
DELETE               /groups/{groupAddress}/members/{memberResource}/{memberAddress}
```

### Tipos de ativo
```
GET,POST,PUT         /lamp-types           (deprecado — §4.5)
GET,POST,PUT         /luminaire-types
GET,POST,PUT         /bracket-types
GET,POST,PUT         /driver-types
GET,POST,PUT         /controller-types
```
Cada um com seu `/count` (GET) e `/{address}` (DELETE, GET, PUT).

### Logging
```
POST                 /logger-configs
DELETE,GET,PUT       /logger-configs/{loggerAddress}
GET                  /log-reports/count
GET                  /log-reports/{loggerAddress}
```

### Firmware e serviços
```
GET,PUT              /data-packages        (PUT retorna 204 — §5.9)
GET                  /services
GET                  /services/{serviceName}
```

### Observações

- **O anúncio é assimétrico:** no bootstrap **nós** fazemos `POST /device-classes` e `POST /devices` **no CMS**. No nosso lado, `/device-classes` é somente leitura (`GET`) — o CMS consulta o que anunciamos, não cria classes em nós.
- **`PATCH` não remove funções** — só adiciona ou atualiza por `id`. Para remover, o CMS precisa usar `PUT` com a lista completa reduzida (§3.4, p.17–18).
- **`PUT` em `/devices` é destrutivo** no array de funções: substitui a definição inteira, apagando as ausentes.
- **`/data-packages` PUT retorna 204** e o processo de firmware é assíncrono — o progresso volta por eventos, não por resposta síncrona (§5.9, p.63).

---

## 6. Perguntas em aberto para a EXATI

Sobre certificados (nenhuma regra está documentada):

- Há restrição de **algoritmo/tamanho de chave** — aceitam EC P-256 ou só RSA?
- O **CN/SAN do leaf precisa casar com algo específico** (hostname do `gatewayUri`, identificador de gateway registrado)?
- Aceitam **cadeia com intermediária**, ou o leaf tem que ser assinado direto pela CA que subimos?
- O **Generic Product Certificate** é utilizável? A chave privada dele não aparece como baixável.
- Depois de subir a CA, a ativação é **imediata ou precisa de aprovação**?

Sobre callbacks:

- O certifier faz alguma **sonda de alcançabilidade** no `gatewayUri` antes ou durante o bootstrap?
- Qual o **timeout e a política de retry** dele nas chamadas ao nosso gateway?
- Ele exige que o nosso certificado de servidor seja emitido pela **mesma CA** que subimos para o mTLS de cliente?
