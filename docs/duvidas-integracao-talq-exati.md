# Dúvidas de integração TALQ — pauta para a EXATI

Fontes cruzadas:

- Portal EXATI: `https://docs.iothub.exati.com` (49 páginas, mapeadas via sitemap)
- `20250723-TALQ-Specification-Approved-Version-2.6.3.pdf`
- `talq-data-model-2-6-3-online.json` (autoritativo para schemas)
- `talq-api-gateway-2-6-3-online.json` / `talq-api-cms-2-6-3-online .json`

> **Regra de precedência:** a capa da spec e a §3.4 (p.19) dizem que **o PDF não é autoritativo — os três arquivos OpenAPI prevalecem** em qualquer conflito. Onde o portal da EXATI diverge do OAS oficial, cabe a eles dizer se é extensão intencional ou erro de importação.

---

## Situação das 6 dúvidas

| # | Pergunta | Portal EXATI | Spec/OAS oficial | Ação |
|---|---|---|---|---|
| 1 | `gatewayAddress` | parcial | respondido (§5.1 p.40) | confirmar NIL UUID |
| 2 | `clientAddress` | **divergente** | respondido (§3.3 p.17) | **decidir** |
| 3 | `attributes` × `vendorAttributes` | não | respondido (`FunctionDesc`) | informativo |
| 4 | `commands->states` / `levels` | **não** | respondido (`CommandsDesc`) | **pedir correção do portal** |
| 5 | valores de `unit` | **não** | respondido (56 valores) | **decidir duplicatas** |
| 6 | `types`/`commands` em vendorAttributes | não | respondido (`allOf`) | confirmar campo `name`/`type` |

---

## 1. Onde acessamos/geramos o `gatewayAddress`

**Resposta: nunca geramos. Quem gera é o CMS.**

O portal diz que o CMS cria o `gatewayAddress` e o devolve junto do `cmsAddress` no `POST /devices`. O que ele **omite** é a regra que quebra o bootstrap:

> §5.1 passo 1 (p.40): o primeiro `POST /devices` deve usar **NIL UUID** — `00000000-0000-0000-0000-000000000000`. Se o gateway enviar qualquer outro UUID, o CMS **deve rejeitar** a requisição.

Gateway que pré-gera o próprio ID **falha o bootstrap**.

O NIL UUID também funciona como dica de idempotência "find-or-create" (§5.3, p.47):

| UUID enviado | Já existe duplicata | Resultado |
|---|---|---|
| NIL | sim | retorna o endereço existente, sem erro |
| UUID real | sim | **409 Conflict** |

**Perguntar:** o IoT Hub implementa a rejeição de UUID não-NIL no primeiro POST? E o find-or-create do NIL em duplicata?

---

## 2. Onde acessamos/geramos o `clientAddress`

**Resposta: não é um campo que se gera — é o UUID de quem envia a requisição**, como *query parameter* (não header, não corpo).

| Quem envia | `clientAddress` = | Origem do valor |
|---|---|---|
| Gateway | `gatewayAddress` | resposta do `POST /devices` |
| CMS | `cmsAddress` | mesma resposta do `POST /devices` |

Única requisição sem ele: o `POST /devices` inicial — nesse momento o gateway ainda não tem identidade TALQ.

### Divergência dura

| Fonte | `clientAddress` |
|---|---|
| OAS gateway oficial | **required em 87 de 87** operações |
| OAS CMS oficial | **required em 56 de 56** operações |
| Portal EXATI | `required: false` |

Spec §3.3 (p.17): obrigatório em toda requisição; sem ele a requisição é inválida.

**Perguntar:** o IoT Hub aceita requisição sem `clientAddress`? Se aceita, é tolerância deliberada ou o portal está com a marcação errada?

---

## 3. Diferença entre `attributes` e `vendorAttributes`

Ambos vivem dentro de `FunctionDesc` e compartilham o **mesmo schema base**, `AttributeDesc`:

```
VendorAttributeDesc = allOf [ AttributeDesc, { scope, type } ]
```

`vendorAttributes` acrescenta **dois campos obrigatórios** que `attributes` não tem:

- **`scope`**: `configuration` | `measurement` | `event` | `operational`
- **`type`**: enum com 45 tipos — `Attribute`, `AttributeBinary`, `AttributeBoolean`, `AttributeFloat`, `AttributeInteger`, `AttributeString`, `AttributeLevelState`, `AttributeCommand`, `AttributeLocation`, `AttributePercent`, … (lista completa no data-model)

**A lógica por trás disso:** em `attributes` você declara atributos **da função TALQ padrão** — o tipo e o escopo já estão definidos na spec, seria redundante repetir. Em `vendorAttributes` o atributo é definido pelo fornecedor, então ele precisa se autodescrever.

Campos herdados por ambos (de `AttributeDesc`): `name` (obrigatório), `description`, `minValue`, `maxValue`, `regEx`, `readOnly`, `enumValues`, `unit`, `commands`, `types`.

**Regra correlata (§4.4, p.37):** atributos vendor desconhecidos devem ser **aceitos silenciosamente**. Retornar erro é violação de spec. Vale também para *eventos* vendor.

**Nota sobre `readOnly`:** atributo marcado `readOnly` não pode ser inicializado pelo CMS. Se o CMS mandar mesmo assim, a requisição **deve ser aceita** — só não altera o valor no lado da ODN. Não é para retornar erro.

---

## 4. Formatação de `states` e `levels` em `attributes -> commands`

**No portal isso está como `type: object` puro, sem estrutura alguma — não dá para implementar a partir dele.** A estrutura real está no `CommandsDesc` do data-model oficial.

### `CommandsDesc` completo

```json
{
  "scope": "default",
  "states": [ ... ],
  "levels": [ ... ],
  "attributes": [ ... ]
}
```

- **`scope`**: `default` | `override` | `programs` — default é `default`
- **`attributes`**: enum de 7 — `reason`, `cmsRefId`, `refAddress`, `start`, `expiration`, `rampToLevelTime`, `rampFromLevelTime`

Esses dois o portal documenta. `states` e `levels` não.

### `states` — array de `State`

`State` é objeto com `name` obrigatório e **discriminator em `name`**. 13 valores possíveis:

`IntegerState`, `FloatState`, `BooleanState`, `TextState`, `RGBState`, `LevelState`, `PositionedTextState`, `CCTColorState`, `RGBWAFColorState`, `XYColorState`, `LevelAndCCTColorState`, `LevelAndRGBWAFColorState`, `LevelAndXYColorState`

Cada subtipo concreto acrescenta um `value` do tipo apropriado:

```json
"states": [
  { "name": "BooleanState",  "value": true },
  { "name": "LevelState",    "value": 80 },
  { "name": "CCTColorState", "value": 3000 }
]
```

- `LevelState.value` → `DimLevel` (integer 0–100)
- `BooleanState.value` → boolean
- `CCTColorState.value` → number (Kelvin, ex. 3000)

### `levels` — array de faixas aceitas

```json
"levels": [
  { "start": 0,  "end": 0   },
  { "start": 10, "end": 100 }
]
```

- `start` **obrigatório**, `end` opcional
- ambos são `DimLevel` = **integer, mínimo 0, máximo 100**
- o exemplo acima significa: aceita desligado (0) ou 10–100%, mas **não** aceita 1–9%

**Perguntar:** o portal vai ser corrigido para expor esses schemas? Enquanto isso, validamos contra o data-model oficial e assumimos que o IoT Hub segue?

---

## 5. Valores válidos de `unit`

O portal **não traz enum nenhum** — só a descrição em texto livre. O OAS oficial traz enum fechado de **56 valores**, e ele vale tanto para `attributes[].unit` quanto para `types.properties[].unit` (é o mesmo enum, definido em `AttributeDesc` e replicado em `TypePropertyDesc`).

```
Amperes, AmperesPerHour, CubicMeters, CubicMetersPerSecond, Date, DateTime,
Days, dBm, Decibels, DecibelsMilliWatts, Degree360, Degrees, DegreesCelcius,
DegreesCelsius, g-force, Hectopascal, Hertz, Hours, Illuminance, Joules,
Kelvin, Kilograms, KilometersPerHour, KiloVoltAmpere, KiloVoltAmpereHours,
KiloVoltAmpereReactive, KiloVoltAmpereReactiveHours, KiloWatt, KiloWattHours,
Lumens, LumensPerWatt, Meters, MetersPerSecond, MicrogramsPerCubicMeter,
MilliAmperes, MilligramsPerLiter, Millimeters, MillimetersPerHour,
NephelometricTurbidityUnit, None, PartsPerMillion, PartsPerThousand, Percent,
Seconds, SiemenPerMeter, SquareMeters, Time, VoltAmpereHours,
VoltAmpereReactive, VoltAmperes, VoltPerSecond, Volts, Watt, Watts,
WattsPerSquareMeter, Years
```

### Três armadilhas — viram pergunta direta

1. **`DegreesCelcius` e `DegreesCelsius` coexistem no enum.** O erro de grafia foi preservado junto da forma correta. Qual o IoT Hub aceita? Os dois? Qual devemos emitir?
2. **`Watt` e `Watts` duplicados**, mesma questão. Idem `dBm` × `DecibelsMilliWatts`.
3. **Contradição na própria spec:** a descrição do campo diz *"either one of the TALQ standard unit types, **or specific based on an extension**"* — mas o schema é enum **fechado**. Unidade de extensão passa na validação do IoT Hub ou é rejeitada?

---

## 6. `types` e `commands` em `vendorAttributes` têm o mesmo formato de `attributes`?

**Sim — formato idêntico, por herança.**

`VendorAttributeDesc` é `allOf [AttributeDesc, {scope, type}]`, então herda `commands` (`CommandsDesc`) e `types` (`TypeDesc`) **sem nenhuma alteração**. A única diferença entre os dois é o par `scope` + `type` obrigatório descrito no item 3.

### Colisão de nomes — cuidado em code review

| Campo | O que é |
|---|---|
| `type` | string — o tipo TALQ base do atributo vendor (`AttributeFloat`, …) |
| `types` | objeto `TypeDesc` — com `address` + `properties[]` |

São campos diferentes com nomes quase iguais, no mesmo objeto.

### `TypeDesc` oficial

```json
{
  "address": "<identificador único>",
  "type": "LampType | LuminaireType | DriverType | ControllerType | BracketType",
  "properties": [ /* TypePropertyDesc */ ]
}
```

`TypePropertyDesc` tem: `name` (obrigatório), `description`, `minValue`, `maxValue`, `regEx`, `readOnly`, `enumValues`, `unit`.

### Divergência a confirmar na tela

No portal, o `TalqTypeDescDto` aparentemente chama esse campo de **`name`**; no data-model oficial o `TypeDesc` chama de **`type`**. O enum de 5 valores é o mesmo nos dois. *Confirmar visualmente antes de afirmar na reunião — essa leitura veio de extração automática da página.*

**Nota de migração (§4.5, p.38–39):** `LampType` está **deprecado** e foi dividido em quatro — `LuminaireType`, `BracketType`, `DriverType`, `ControllerType`. Ambos ainda funcionam, mas código novo deve usar os substitutos. Vale perguntar qual o IoT Hub espera.

---

## Contexto adicional para a reunião

**As páginas de endpoint do portal estão duplicadas.** O sitemap mostra duas séries completas de IDs para as mesmas 14 páginas de device-class/device (`143205xx–143206xx` e `214141xx`), além de duplicata em groups (`14320610/11` e `24570189/90`). Comparei dois pares e são equivalentes em método, path, params e schemas. Parece import duplicado da mesma spec OpenAPI, e uma delas possivelmente desatualizada — o que explicaria o `unit` sem enum e o `states`/`levels` vazios.

**A ordem do menu lateral do Bootstrap está invertida** em relação à ordem de execução real:

| Ordem no menu | Ordem real de execução |
|---|---|
| classes discovery | 1. gateway announcement |
| device discovery | 2. gateway class announcement |
| services announcement | 3. gateway update |
| gateway update | 4. services announcement |
| gateway class announcement | 5. classes discovery |
| gateway announcement | 6. device discovery |

As 6 subpáginas do bootstrap são apenas páginas de endpoint com a tag `Docs TALQ/Tier 2/Bootstrap process` — **nenhuma delas descreve pré-requisito, ordem ou etapa anterior/seguinte**. A sequência existe só na página-mãe (`bootstrap-process-1603098m0`).

Dependências que travam a ordem (não é convenção, é dado):

- passo 1 primeiro: sem `gatewayAddress` não há o que pôr em `clientAddress`
- passo 3 antes de qualquer coisa vinda do CMS: é o `gatewayUri` que diz ao CMS onde nos achar
- **classes discovery antes de device discovery**: `class` é campo obrigatório em `TalqDeviceDto`; device de classe não anunciada → 404/422
- fora de ordem durante o bootstrap → **403**

---

# Perguntas para a EXATI — lista consolidada

Ordenadas por bloqueio. Os itens A e B travam o trabalho hoje; os demais são decisões de implementação ou correções de documentação.

## A. Bloqueadores

### A1. Certificado de cliente (mTLS) para o certifier

O `iotcertifier` na porta 8443 recusa requisição sem certificado de cliente. Confirmado empiricamente — resposta do próprio servidor:

```json
{"detail":"No client certificate was presented. Ensure your gateway sends its client certificate on port 8443."}
```

Isso **não está documentado em nenhum lugar** do portal (a página de autenticação menciona mTLS só conceitualmente) nem pode estar na spec — a §3.6 (p.22) diz explicitamente que a infraestrutura de confiança é responsabilidade de quem entrega o sistema.

- Como obtemos o certificado de cliente? Quem emite — CA de vocês, ou geramos e vocês registram?
- Em que formato entregar (PEM, PKCS#12)?
- Qual o procedimento de renovação/revogação?
- Qual o certificado ou CA **do servidor** de vocês, para o nosso truststore? O do certifier é auto-assinado (`O=Exati, OU=IoT`) e é enviado sem cadeia, então não valida em truststore padrão.
- O dashboard do certifier tem ponto de emissão/download desse certificado? (Não localizamos.)

### A2. Qual ambiente devemos homologar

Existem dois endereços com **modelos de autenticação incompatíveis**, ambos testados:

| | `iot.exati.com.br/staging` | `iotcertifier.exati.com.br:8443` |
|---|---|---|
| Porta | 443 | 8443 |
| Prefixo | `/staging/talq/…` | `/cms/<token>/…` (sem `/talq`) |
| Cert. do servidor | `*.exati.com.br`, CA pública, válido | auto-assinado, sem cadeia |
| Autenticação | **Basic Auth ou API Key** | **token no path + mTLS** |
| Sem credencial | `401 "use Basic Auth ou API Key"` | `401` (token) / `403` (falta cert) |

- São ambientes do mesmo sistema ou sistemas diferentes?
- O `iotcertifier` é harness de certificação TALQ ou é o CMS de produção?
- Se for produção, por que certificado auto-assinado, sendo que vocês têm wildcard válido?
- O comportamento funcional é idêntico? Passar no staging garante passar no certifier?
- O certifier não aparece em nenhuma página do portal — existe material sobre ele em outro lugar?

## B. Decisões de implementação

### B1. `clientAddress` é obrigatório?

| Fonte | |
|---|---|
| OAS gateway oficial | required em **87 de 87** operações |
| OAS CMS oficial | required em **56 de 56** |
| Portal EXATI | `required: false` |

Spec §3.3 (p.17): obrigatório em toda requisição. O IoT Hub aceita sem? É tolerância deliberada ou marcação errada no portal?

### B2. Duplicatas no enum de `unit`

O enum oficial tem 56 valores e contém pares redundantes:

- **`DegreesCelcius` e `DegreesCelsius`** — o erro de grafia foi preservado junto da forma correta
- **`Watt` e `Watts`**; **`dBm` e `DecibelsMilliWatts`**

Quais o IoT Hub aceita? Qual devemos emitir? E a descrição do campo promete *"or specific based on an extension"* enquanto o schema é enum fechado — unidade de extensão passa na validação ou é rejeitada?

### B3. NIL UUID no primeiro `POST /devices`

Spec §5.1 (p.40): o primeiro POST deve usar `00000000-0000-0000-0000-000000000000`, e qualquer outro UUID **deve ser rejeitado**. §5.3 (p.47): NIL + duplicata retorna o existente; UUID real + duplicata → 409.

O IoT Hub implementa as duas regras?

### B4. `LampType` deprecado

§4.5 (p.38–39): `LampType` foi dividido em `LuminaireType`, `BracketType`, `DriverType`, `ControllerType`. Ambos ainda funcionam na spec. Qual o IoT Hub espera receber?

### B5. Forma da rota no certifier

`GET /groups` responde 403 (rota reconhecida), mas `GET /devices` responde `404 {"detail":"Unknown resource type"}`. Pode ser ordem de validação interna, mas se persistir com o certificado instalado, precisamos entender. Confirmar também que o recurso vai direto na base, sem `/talq`.

## C. Correções de documentação

1. **`commands.states` e `commands.levels` estão como `type: object` vazio** no portal. A estrutura real (`State` com discriminator em `name`, 13 tipos; `levels` como faixas `{start, end}` de `DimLevel` 0–100) só existe no data-model oficial. Não é implementável a partir do portal.
2. **`unit` sem enum** no portal — os 56 valores não aparecem.
3. **Páginas de endpoint duplicadas**: duas séries completas de IDs (`143205xx–143206xx` e `214141xx`) para as mesmas 14 páginas, mais duplicata em groups. Sugere import duplicado de spec OpenAPI, possivelmente uma desatualizada — o que explicaria os itens 1 e 2.
4. **Ordem do menu do Bootstrap invertida** em relação à execução real.
5. **Certifier não documentado** — nem o endereço, nem o mTLS, nem a porta 8443.
6. **`TypeDesc`**: o portal chama o campo de `name`, o data-model oficial chama de `type`. *(confirmar visualmente antes de levantar)*

---

> **Lembrete de precedência:** capa da spec e §3.4 (p.19) — o PDF não é autoritativo; os três arquivos OpenAPI prevalecem. Onde o portal diverge do OAS oficial, cabe à EXATI dizer se é extensão intencional ou erro de importação.
