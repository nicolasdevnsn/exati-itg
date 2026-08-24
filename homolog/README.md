# Homolog — bootstrap iotcertifier (sessão 2026-08-18)

Kit de homologação: certificados novos + bodies de cada rota do bootstrap,
adaptados ao nosso ambiente de iluminação pública (perfil `lighting`).

**Fonte da sintaxe:** os 3 OpenAPI oficiais 2.6.3 (que **prevalecem sobre o PDF
e o portal**, spec §3.4). Os fatos validados em 12/08/2026 (rotas, ordem,
parâmetros, NIL UUID) estão em `../docs/duvidas-abertas-2026-08-12.txt`.
Os bodies em si foram reconstruídos a partir do OAS — os payloads exatos de
12/08 não ficaram gravados.

---

## 1. Certificados (`certs/`)

| Arquivo | Papel |
|---|---|
| `nansen-ca-homolog.crt` | CA — é **só isto** que sobe no dashboard (campo CA) |
| `nansen-ca-homolog.key` | chave da CA — **nunca sai da nossa infra** |
| `gw-homolog.crt` | leaf do gateway (CN/SAN `gateway-homolog.nansen.com.br`) |
| `gw-homolog.key` | chave privada do gateway (keystore do exati-itg) |
| `gw-homolog.csr` / `.ext` | intermediários da emissão (auditoria) |

Verificado: `openssl verify` OK, EKU `clientAuth,serverAuth`, RSA 2048/SHA-256,
leaf 825 dias, CA 10 anos.

> **ATENÇÃO — hostname provisório.** O SAN é `gateway-homolog.nansen.com.br`,
> mas a decisão A4 (hostname público do gateway) segue aberta. O SAN **tem** que
> ser igual ao host do `gatewayUri` anunciado — quando o hostname definitivo
> for decidido, reemitir o leaf (só passos 2–3 da geração; a CA continua valendo
> e não precisa reenviar ao dashboard).

## 2. Onde colocar no dashboard (validado em 12/08 — as DUAS camadas são obrigatórias)

1. **`nansen-ca-homolog.crt` → "Certificados CA do Vendor"** (campo *PEM do
   Certificado CA*). Push assíncrono para o load balancer — esperar a coluna
   **SINCRONIZADO** (minutos).
   *Armadilha comprovada:* em 12/08 o cadastro self-service **não alimentou o
   load balancer** — precisou de ação do *platform admin* da EXATI. Se após
   sincronizar continuar `client_cert_validation_failed`, pedir à EXATI o
   registro administrativo da CA. Fingerprint SHA-256 desta CA (para passar à EXATI):
   `7D:F1:84:03:18:74:23:22:1F:2C:1B:C3:75:F3:44:74:2C:36:60:AE:2E:27:48:D2:02:70:13:EC:78:DD:2E:EC`
2. **`gw-homolog.crt` (leaf) → "Certificados de Produto"** do produto. Pinning
   por aplicação, vale imediatamente. **Registrar só a CA NÃO basta.**
3. **Baixar do dashboard** a `CA Raiz (Vendor Certifier CA)` → truststore nosso
   (validar o servidor deles e os callbacks que eles farão em nós).

## 3. Como disparar (curl daqui é Schannel — não faz mTLS com PEM; usar openssl)

```bash
export OPENSSL_CONF="$(cygpath -w /usr/ssl/openssl.cnf)"
export MSYS_NO_PATHCONV=1
HOST=iotcertifier.exati.com.br; PORT=8443; BASE=/cms/S4X3kCYoNqTqsd7-wyz7D5n9e2TDLRMF2mjdD7XMwfk

req() {  # req <METODO> <PATH_COM_QUERY> [arquivo_body.json]
  local body=""; [ -n "$3" ] && body=$(cat "$3")
  printf '%s %s HTTP/1.1\r\nHost: %s:%s\r\ntalq-api-version: 2.6.0\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
    "$1" "$BASE$2" "$HOST" "$PORT" "${#body}" "$body" \
  | openssl s_client -connect $HOST:$PORT -servername $HOST \
      -cert certs/gw-homolog.crt -key certs/gw-homolog.key -quiet
}
```

Cabeçalho/params obrigatórios pelo OAS em toda rota:
`talq-api-version: 2.6.0` + query `talqRequestId=<uuid novo por request>`.
`clientAddress` é obrigatório pelo OAS. No passo 1 use
`clientAddress=00000000-0000-0000-0000-000000000000` (NIL UUID) — omitir dá
201, mas o audit GW_BV_012 REPROVA (ver §"Reprovados" abaixo; corrigido em
19/08, a instrução anterior "manter assim" estava errada). Nos demais passos,
`clientAddress=<GW>`.

## 4. As rotas e seus bodies

| Passo | Request | Body | Resultado |
|---|---|---|---|
| 1 | `POST /devices?clientAddress=00000000-0000-0000-0000-000000000000&talqRequestId=<uuid>` | `passo1-POST-devices.json` | **201 em 18/08/2026** (sem clientAddress — GW_BV_012 reprova; próximo bootstrap DEVE incluir) |
| 2 | `POST /device-classes?clientAddress=<GW>&talqRequestId=<uuid>` | `passo2-POST-device-classes.json` | **201 em 18/08/2026** |
| 3 | `PATCH /devices/<GW>?clientAddress=<GW>&talqRequestId=<uuid>` | `passo3-PATCH-devices-gatewayAddress.json` | **200 em 18/08/2026** |
| 4 | (CMS → nosso `gatewayUri`) | — | PENDENTE: exige host público + rotas de callback no ar (NÃO bloqueia o gate do lado CMS) |
| 5 | `POST /services?clientAddress=<GW>&talqRequestId=<uuid>` | `passo5-POST-services.json` | **201 em 18/08/2026** — valores de homolog, revisar p/ produção |
| 6 | `POST /device-classes?clientAddress=<GW>&talqRequestId=<uuid>` | `passo6-POST-device-classes-zenix.json` | **201 em 18/08/2026** — `NansenZenixClass` (Basic+LampActuator+ElectricalMeter) |

**DESCOBERTA 18/08/2026 — o que fecha o "Bootstrap not complete":** o gate do
CMS abriu após passo 5 (services) **+ passo 6 (classe de dispositivo final)**.
Só services (passo 5) NÃO bastou — o 403 persistiu até anunciarmos a
`NansenZenixClass`. O passo 4 (CMS→gatewayUri) **não é pré-requisito** do gate:
`GET /groups` já responde `200 []` sem nosso host público existir.

Valores anunciados no passo 5 (CONTRATO desta identidade — `/services` só tem
POST, não dá para corrigir sem novo bootstrap): perfil `lighting`;
ControlService: 10 calendários, 20 programas, 10 prog/calendário, 10 switch
points/programa, 4 períodos ativos/programa, dayOffset 12,
tipos AbsoluteActivePeriod/AstroClockActivePeriod/FixedControlEffect;
DataCollect: 10 loggers, Periodic+Immediate, minCollectionTime 900s;
Groups: 50 grupos × 500 membros.

## Identidades desta rodada (EXECUTADA em 20/08/2026 — PRODUTO NOVO, log limpo)

- **Motivo do produto novo:** GW_BV_012 audita o LOG de requisições do token
  (todas as POST /devices), não só a última — os anúncios errados de 12/08 e
  18/08 reprovavam para sempre. Log limpo = token novo = produto novo no
  dashboard. Armadilha: o leaf não pode estar pinado em 2 produtos
  ("impressão digital já existe") — remover do produto antigo antes.
- Token novo: `9yTZDDpe4H13mObY6AkfXnIxBGdOdfXxOqNawjg235Y`
- `gatewayAddress` **6df4b4cd-da48-4448-bfd7-bba3f5216bf2**
- Passos 1/2/3/5/6 → 201/201/200/201/201; `GET /groups` → `200 []` (gate aberto)
- Passo 1 com `clientAddress=NIL` na query; passo 3 SEM `address` no body.
- **Passo 7 (20/08):** anunciado 1 dispositivo final ZENIX
  (`passo7-POST-devices-zenix.json`, address NIL → CMS atribuiu
  **93c751e8-2c06-460e-8d68-31f5e4774b43**) → 201. Motivo: GW_DDRS_001
  ("No device with BasicFunction found") — a bateria DDRS (On Demand Data
  Request Service) exige ≥1 dispositivo (não basta a classe).
  ⚠️ Manter o talq-seed do servidor em sincronia: o TalqGatewayStore precisa
  conhecer este device quando o certifier chamar GET /devices/{addr}/{fn}.
- **Correção 20/08 ("No device with CommunicationFunction found"):** a classe
  já declarava `comm-01`, mas o DEVICE anunciado ao CMS (passo 7) foi enviado
  sem a instância. Fix: `PATCH /devices/93c751e8…` no CMS adicionando o
  `comm-01` (physicalAddress/communicationType/communicationFailure) → 200;
  store local sincronizado SEM restart via `POST /seed/device-classes` +
  `POST /seed/devices` (upsert — não apaga o resto do estado em memória).
  Obs.: `PUT /device-classes` no certifier responde 404 (rota não suportada
  lá); classe só cresce via novo anúncio/bootstrap.
- **Correção 21/08 ("No device with LampMonitorFunction found", GW_DDRS_005):**
  mesma receita — classe cresceu com `lampmon-01` (LampMonitorFunction:
  lampFailure M + operatingHours + switchOnCounter) e o device `93c751e8…`
  ganhou a instância com valores. DESCOBERTA: **re-POST de `/device-classes`
  com o mesmo nome e a classe crescida → 201** (o certifier aceita grow via
  re-anúncio; não precisa de PUT nem bootstrap novo). CMS: POST classe 201 +
  PATCH device 200. Local: seed files + TalqTypeCatalog atualizados, app
  sincronizado via /seed sem restart.
  **PORÉM o PATCH não bastou** — o erro persistiu na bateria seguinte: o 200
  do `PATCH /devices/{addr}` no CMS NÃO acrescenta funções ao inventário que
  o seletor de testes usa (e `PUT /devices` e `PUT/GET /devices/{addr}` lá
  respondem 404 "Unknown resource type"). **Regra prática: no certifier,
  função nova em device já anunciado = anunciar um DEVICE NOVO completo.**
  Fix definitivo: POST de "ZENIX SIP homolog 002" com as 5 funções desde o
  nascimento → 201 com `lampmon-01` no echo; CMS atribuiu
  **9276bb59-2476-4bef-a0a3-a8b02d709570**. Local: 2º device no
  end-devices.json + /seed upsert (HTTP 200 verificado).
- **Correção 21/08 tarde ("No device with TemperatureSensorFunction found",
  GW_DDRS_009):** mesma receita direto (classe cresce + device novo completo).
  Classe ganhou `temp-01` (TemperatureSensorFunction: `temperature` M +
  `temperatureHighThreshold` como atributo de CONFIGURAÇÃO — o passo 2 do
  DDRS lê um atributo de configuração e compara com o valor corrente).
  CMS: classe 201 + "ZENIX SIP homolog 003" 201 (temp-01 no echo), address
  **1104ebfb-704d-4587-9520-db5361b03f46**. Local: seed + catálogo + /seed
  upsert verificados. ⚠️ Se a bateria pedir outra função ("No device with
  XFunction found"), repetir ESTA receita — e no fim validar com o produto
  (formulário do Ayres) quais funções o ZENIX real declara.
- **Correção 24/08 ("No device with BatteryLevelSensorFunction found",
  GW_DDRS_014):** a tentativa anterior tinha editado SÓ os arquivos locais e
  ainda com drift (batt-01 no device 003 local, que no CMS nasceu sem — 
  retrofit é impossível lá). Fix: batt-01 removido do 003 local (espelha o
  CMS); passo6 sincronizado com a classe do seed; classe re-anunciada com
  BatteryLevelSensorFunction (batteryLevel M % + batteryLevelLowThreshold
  config + batteryLevelTooLow) → 201; **"ZENIX SIP homolog 004"** nasceu
  completo (7 funções) → 201 com batt-01 no echo, address
  **f9abf266-8136-4fc3-875c-495912f3b6e9**; /seed upsert verificado. Sem
  mudança de código Java → sem rebuild → gatewayUri preservado.
- **Correção 24/08 lote (GW_DDRS_016/022/024 + GW_DV_005):** classe cresceu de
  uma vez com Photocell (photocellOutput M binário ON/OFF + onLevel config que
  o teste lê), LocationSensor (locationChanged M + locationChangedThreshold
  config) e Orientation (orientationChanged M + orientationChangedThreshold
  AttributeOrientation {phi,theta,psi}) → 201; **"ZENIX SIP homolog 005"**
  nasceu com as 10 funções → 201, address
  **629e9471-0a57-4797-b883-c15a5fb47445**. GW_DV_005 (PATCH de atributo →
  409): o certifier manda `{"value": …}` SEM o campo `type`; o DeviceValidator
  agora adota o tipo declarado quando o wrapper vem sem discriminador
  (verificado: typeless PATCH → 200 e o valor é gravado tipado; type errado
  EXPLÍCITO segue 409, GW_BV_009 preservado). Obs.: catálogo passou de Map.of
  (limite de 10 pares) para Map.ofEntries.

## Rodada anterior (19/08/2026 — token antigo, superada)

- `gatewayAddress` **0b22d6c4-5357-4053-8d54-700c418315f6**
- `cmsAddress` `10000000-0000-0000-0000-000000000001` (mesmo das rodadas anteriores)
- Passo 1 com `clientAddress=NIL` na query (fix GW_BV_012); passo 3 SEM o campo
  `address` no body (fix GW_BV_016). Passos 1/2/3/5/6 → 201/201/200/201/201.
- Gate confirmado aberto: `GET /groups` → `200 []`.
- Resíduos no certifier (sem DELETE que funcione):
  - `4c06a713-d01b-4146-ae20-f3c77e2bad33` (12/08)
  - `81eaf17a-c10e-4a98-a353-7641333d6c51` (18/08, passo 1 sem clientAddress)
  - `39dbf483-6128-4163-be84-5823c96c6484` (19/08, descartado: `talqRequestId`
    foi VAZIO — `uuidgen` não existe nesta máquina; gerar UUID com
    `powershell.exe -NoProfile -Command "[guid]::NewGuid().Guid"`)
- `<DECISAO_A2>` — limites numéricos reais (decisão interna pendente; o que
  anunciarmos vira contrato de conformidade).

## 4b. Resultado da bateria automática (18/08/2026): 84 testes — 9 aprovados, 14 reprovados, 61 não executados

**Aprovados (9):** os 5 de "Verificação de Bootstrap" (GW_BS_001–005) e os 4
audits de conteúdo (GW_BV_014 Typed Attributes, 015 Entity Schema Compliance,
017 supportedProfiles Enum, 018 Attribute Types Match Spec) — ou seja, TODO o
conteúdo dos bodies passou.

**Não executados (61) + 12 dos reprovados ("HTTP client not available", 0ms):**
todos exigem o certifier atuando como CLIENTE contra o nosso `gatewayUri` —
que não existe (host público pendente + 44 rotas de callback não implementadas).
Não é defeito dos requests; é o gap conhecido.

**Reprovados por causa NOSSA (2) — corrigir no próximo bootstrap:**

1. **GW_BV_012** — passo 1 DEVE levar `clientAddress=00000000-0000-0000-0000-000000000000`
   na query. Em 12/08 e 18/08 omitimos (deu 201, mas o audit reprova):
   *"both gatewayAddress and clientAddress must be 00000000-… : clientAddress
   query param is missing"*. Isto CORRIGE o registro R3 do arquivo de dúvidas —
   "omitir funciona" vale para o 201, NÃO para a certificação.
2. **GW_BV_016** — no PATCH `/devices/{addr}`, OMITIR o campo `address` do body
   (a regra é "ausente OU igual"; o audit deles cola a query string na
   comparação da URL, então com o campo presente reprova mesmo com valores
   iguais). Enviar o body só com `name`, `class` e `functions`.

O bootstrap do gateway `81eaf17a…` já foi consumido — para zerar os 2 itens é
preciso bootstrap NOVO (gateway novo) com as duas correções.

## 5. Notas de sintaxe que valem discussão (conferir no review)

- **Formato de atributo de função é objeto-wrapper**: cada atributo da
  `GatewayFunction` vai como `{"type": "AttributeUri|AttributeString|AttributeFloat|AttributeInteger", "value": ...}`,
  direto como propriedade do objeto da função (não existe array `attributes`
  no Device — isso é só no DeviceClass, como `AttributeDesc` `{"name": ...}`).
- `POST /devices`, `POST /device-classes` e `POST /services` recebem **array**;
  `PATCH /devices/{address}` recebe **objeto único**.
- Passo 1 com `address` = NIL UUID `00000000-0000-0000-0000-000000000000`
  (validado 12/08, spec §5.1 p.40). Incluí a `GatewayFunction` mínima no body;
  se o certifier devolver 400, reduzir ao mínimo do schema
  (`address`+`name`+`class`) — os obrigatórios entram todos no passo 3.
- No perfil `lighting` os atributos M da GatewayFunction (lado gw) são:
  `cmsUri`, `cmsAddress`, `gatewayUri`, `gatewayAddress`, `vendor`, `crlUrn`
  e **`retryPeriod` (deprecated porém mandatório — armadilha conhecida)**.
  Os retry/numberOfRetries novos são opcionais (default 3) — anunciei mesmo
  assim, é o contrato de liveness que nos interessa.
- `crlUrn`: apontei para um caminho de CRL no nosso host — **ainda não existe**;
  publicar uma CRL (mesmo vazia) da CA homolog ou negociar o valor com a EXATI.
- No enum oficial de Service o nome é **`DataCollectService`** — não
  "DataCollectionService" como está anotado na decisão A1.
- `TalqResourceClient.modifyDevice()` continua com o bug de omitir
  `clientAddress` — se o passo 3 for disparado pelo código em vez de manual,
  corrigir antes.
