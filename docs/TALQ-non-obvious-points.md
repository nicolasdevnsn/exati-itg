# TALQ Specification 2.6.3 — Non-Obvious Points

Source: `20250723-TALQ-Specification-Approved-Version-2.6.3.pdf`
Reading: full text, two passes.

Page references match the PDF's internal "X of 66" page numbering (visible at the bottom of each page in the rendered PDF). When a topic spans several pages, the primary page is given.

The points below are the things that bite implementers. They're not always in the abstract or executive summary but matter for certification, interoperability, and avoiding subtle bugs.

---

## A. Architecture & Protocol

1. **The PDF is NOT authoritative — the OAS files win.** Cover page and §3.4: in any conflict between this document and the three OpenAPI v3 files (`cms`, `gateway`, `data-model`), the OAS files supersede. Never close an issue on text alone. *(p. 2 + p. 19)*

2. **Bidirectional REST = two HTTP servers + two HTTP clients.** Both CMS and Gateway must run a server AND a client. The CMS is not a passive endpoint — the Gateway constantly POSTs to it (log reports, device announcements). NAT/firewall planning required on the Gateway side. *(§3.2, p. 16)*

3. **`clientAddress` is mandatory on every single request** — and it's a query parameter, not a header. Identifies the UUID of the *sender*. Skip it and the request is invalid. *(§3.3, p. 17)*

4. **`Content-Length` is mandatory → chunked transfer encoding is implicitly banned.** §3.5 explicitly. You must size payloads before sending. Many HTTP stacks default to chunked; you'll have to disable it. *(§3.5, p. 20)*

5. **The server must echo the client's `Content-Encoding`.** Unlike normal HTTP (where the server chooses based on `Accept-Encoding`), in TALQ the server replies in the same encoding (gzip/deflate/none) the client used. Behaving "like a normal web server" violates the spec. *(§3.5, p. 20)*

6. **All three content encodings are mandatory on the server side** (none, deflate, gzip). A client request using an unsupported encoding must return **415 Unsupported Media Type**. *(§3.5, p. 20)*

7. **MINOR version mismatches: the higher side must downgrade.** §3.5 — if a 2.6 system talks to a 2.4, the 2.6 side adapts down. The lower-version side stays naive. The newer implementer carries the compatibility cost. *(§3.5, p. 21)*

8. **`cache-control: no-transform` is required on requests AND responses.** Easy to forget; proxies can otherwise mutate payloads (TALQ allows unencrypted HTTP in some cases — see §3.6). *(§3.5, p. 20)*

9. **HTTP/1.1 is mandated** (§3.5). HTTP/2 is not part of the spec. Reason given: connection reuse via keep-alive. *(§3.5, p. 20)*

10. **Three request-tracking dimensions, all separate:** `talq-api-version` (header), `talqRequestId` (query param, client-generated UUID), `talqOriginRequestId` (query param, links a request to the one that caused it). Missing any of these when expected can fail certification. *(§3.5, p. 21)*

11. **`range` and `content-range` headers are used for resumable firmware downloads** (§3.5 + §5.9 + Data Package Transfer). Unit MUST be `bytes`. Failures return **416 Requested Range Not Satisfiable**. *(§3.5, p. 20–21; §5.9, p. 63)*

12. **The `resync` header exists** (listed in §3.5 HTTP header table) but the spec text barely describes it — its detail lives in the OAS files. Plan to look there before claiming you've handled it. *(§3.5, p. 20)*

13. **TLS is mandatory but the trust infrastructure is explicitly out of scope.** Self-signed, CA-signed, mutual TLS — all allowed; deployers decide. *(§3.6, p. 22)*

14. **ODN security is entirely the vendor's responsibility.** TALQ stops at the Gateway. Don't expect the spec to dictate end-to-end security. *(§3.6, p. 22)*

15. **TLS can be disabled for testing** — "Vendors may be required to disable it to run tests and to support non-production environments." Useful to know for local PoC work. *(§3.6, p. 22)*

16. **HTTP 429 (Too Many Requests) is listed but rate limits are not specified** — entirely vendor-dependent. Build clients defensively. *(§3.5, p. 21)*

---

## B. Data Model

17. **There is no "TALQ device type registry."** Vendors define their own device classes by composing TALQ functions. A "Light Point Controller" from Vendor A and Vendor B can have completely different function sets — the CMS learns each class via the bootstrap announcement. There are no canonical device shapes, only canonical *functions*. *(§4.1, p. 23)*

18. **Functions are instanced multiple times per device** (e.g. two `LampActuator` instances). They get string ids like `lampActuator001` set by the vendor — not addresses. Don't model functions as a set keyed by type. *(§4.1 example, p. 23; §5.2, p. 46)*

19. **"Status attributes" exist but are not documented — by design.** §2.1: if implemented, they're Boolean and have the same name as the corresponding event. So a `temperatureTooHigh` event implies a possible `temperatureTooHigh` boolean status attribute — same name, different thing. *(§2.1, p. 13)*

20. **Profiles change what's mandatory.** Same function can be optional under "Lighting" and mandatory under "Cabinet Control." The Gateway announces which profiles it supports during bootstrap; the CMS must hold a per-profile mandatory/optional table. There is no global "is this attribute required" answer. *(§4.3, p. 35)*

21. **Seven profiles total:** Lighting, Waste Management, Environmental Monitoring, Smart Traffic, Smart Parking, Lighting Asset Management, Cabinet Control. *(§4.3, p. 35)*

22. **`Time` function is technically mandatory but flagged optional until 3.0.0** for backward compatibility. A trap if you certify against 2.6.3 and assume the documented "mandatory" is enforced. *(§4.2 Time row, p. 29)*

23. **3.0.0 is a planned breaking release.** The Time function note explicitly says "This will be modified at 3.0.0." Anything in 2.x that mentions 3.0.0 is a signal that behaviour will break. *(§4.2, p. 29)*

24. **`FillingLevelSensor` and `LampType` are deprecated.** Replaced by `LocationSensorFunction` and `LuminaireType`/`BracketType`/`DriverType`/`ControllerType` (the four-way split). Both still work but new code should use the replacements. *(§4.2, p. 28; §4.5, p. 38)*

25. **`LampType` splits into FOUR types in the new model.** Luminaire, Bracket, Driver, Controller — each addressable separately, all referenced by the Luminaire Asset function. Migration is non-trivial. *(§4.5, p. 38–39)*

26. **`applicationType` is a near-universal attribute** appearing on virtually every function. Used to indicate which use case the function serves. Easy to miss in modelling. *(§4.2 function table, p. 25–32)*

27. **Cross-function references exist within a device.** `lampTypeID` is referenced by BOTH `LampActuator` AND `LampMonitor` functions. Editing a Lamp Type affects every actuator and monitor that points to it. *(§4.2, p. 25)*

28. **The Gateway function has one instance per supported CMS connection.** A Gateway connecting to 3 CMSs has 3 Gateway function instances. *(§4.2, p. 25)*

29. **The Communication function is at the Gateway level** (ODN-internal comms), but individual devices may emit `communicationFailure` events via their Basic function. Two layers, two places. *(§4.2, p. 25)*

30. **`cmsURI` is an attribute of the Communication function** — odd place for it semantically; not on the Gateway function. *(§4.2, p. 25)*

31. **WMO standards cascade in.** Atmospheric Sensor, Wind Sensor, Precipitation Sensor, Sky Sensor all explicitly comply with "Guide to Instruments and Methods of Observation (WMO-No. 8)". TALQ inherits these standards. *(§4.2, p. 29–30)*

32. **Vendor-specific attributes must be silently accepted, never error.** Even if you don't understand them, returning an error is a spec violation. Treat unknown attributes as pass-through metadata. *(§4.4, p. 37)*

33. **Vendor-specific *events* appear in event log reports** and follow the same silent-accept rule. The OAS files list the standard events; anything else may be vendor-defined. *(§4.4, p. 37)*

---

## C. Bootstrap & Identity

34. **Gateway addresses are assigned by the CMS, not chosen by the Gateway.** The first POST must use NIL UUID (`00000000-...`); if the Gateway sends any other UUID, the CMS **must reject** the request. Self-hosted Gateways that pre-generate IDs will fail bootstrap. *(§5.1 step 1, p. 40)*

35. **NIL UUID = "find-or-create" idempotency hint.** On device creation: if a duplicate exists and the CMS sent NIL, the Gateway returns the existing address (no error). If the CMS sent a real UUID and there's a duplicate, the Gateway returns **409 Conflict**. *(§5.3, p. 47)*

36. **`/device-classes` is POSTed TWICE during bootstrap** — once in step 1 for the Gateway's own class, once in step 3 for all other ODN device classes. Easy to read as a single step. *(§5.1 steps 1 & 3, p. 40–42)*

37. **Empty `/device-classes` POST = bootstrap-done signal.** There is no explicit "bootstrap finished" message; an empty list in step 3 tells the CMS the Gateway has nothing more to announce. *(§5.1 step 3, p. 42)*

38. **During bootstrap, CMS must reject any out-of-band Gateway requests with 403.** The Gateway is restricted to the three bootstrap operations and nothing else during that phase. *(§5.1, p. 40)*

39. **The CMS sends its own PATCH at the end of step 1** to set the `cmsAddress` attribute on the Gateway. The Gateway doesn't learn the CMS's TALQ address until then. *(§5.1 step 1, p. 40–41)*

40. **A device class can grow but never shrink.** To remove an attribute from a class, you must announce a *new* class. In-place reduction is forbidden — protocol-level versioning of device classes is your responsibility. *(§5.1 step 3, p. 43)*

41. **Multi-CMS Gateways must notify pre-existing CMS instances when a new CMS attaches.** Support is optional, but if claimed, must be implemented. *(§5.1, p. 40)*

42. **Two resync flows: CMS-initiated vs Gateway-initiated.** CMS deletes the Gateway-as-device → Gateway purges and restarts bootstrap. Gateway deletes itself on the CMS → CMS purges and waits for the new bootstrap. Both must be supported. *(§5.1 Resynchronizing a Gateway, p. 44)*

---

## D. Resource Lifecycle

43. **Whole-request rejection on partial errors.** If a bulk PUT/PATCH has one invalid item, the entire request is rejected. No partial success. Don't design clients that expect "errors[]" arrays. *(§3.4, p. 18)*

44. **Forward-reference rule:** A CMS cannot reference a resource it created until that resource has been sent to the Gateway at least once (and symmetric Gateway → CMS). So calendar → control-program → group ordering matters; you must POST in dependency order. *(§4.4, p. 37)*

45. **Referential integrity is server-enforced.** DELETE on an entity referenced elsewhere is rejected. The spec doesn't say how — implementations must implement cascade detection or refuse delete. *(§4.4, p. 38)*

46. **CMS-created resources are visible only to that CMS** (except devices and functions). If multiple CMS are connected, calendars/control-programs/loggers from CMS A are invisible to CMS B. *(§4.4, p. 38)*

47. **PATCH adds-or-updates by function `id`; only PUT can remove functions.** Explicit note in §3.4: to delete functions from a device, you must PUT the full reduced list. PATCH cannot delete. *(§3.4, p. 17–18)*

48. **PUT is fully destructive on the function array** — it replaces the entire device definition. A PUT with a partial function array deletes the missing ones. Use carefully. *(§3.4, p. 17)*

49. **The CMS is the "coordinator of message delivery."** Gateways may buffer/retry, but the CMS is ultimately responsible for delivery state. Don't assume Gateway-side persistence. *(§4.4, p. 38)*

50. **`invalidAddress` event is the standard "you referenced something that doesn't exist" signal.** Generated via the log-report resource, not as an inline error. *(§4.4, p. 38)*

---

## E. Control & Override

51. **Override commands are the preferred mechanism — not direct attribute PUTs.** You *can* PUT/PATCH operational attributes, but the spec recommends POSTing to `/override-commands` instead, because override has expiration, ramp times, reason codes, and resume semantics direct PUTs lack. *(§5.6, p. 55)*

52. **`reason` codes are constrained** to: `unknown | default | override | sensor | program`. Anything else is invalid. *(§5.6, p. 56)*

53. **`rampFromLevelTime` is measured *after* expiration**, not from now. It defines how the device leaves the override level when expiring, not how it enters. *(§5.6, p. 56)*

54. **Override resume is a POST without a `targetCommand` field** (just addresses). Restores the device to the active control program. *(§5.6, p. 56)*

55. **`cmsRefId` exists on override commands and event entries** — for the CMS to correlate its own application-level references against TALQ resources. Easy to overlook. *(§5.6, p. 55; §5.5, p. 54)*

56. **Groups are snapshots, not live references.** If you assign a calendar to a group, then later change the group membership, the calendar stays with the **original members**. New members do *not* inherit it. The CMS owns reconciliation. *(§4.3 group management service, p. 36)*

57. **Assign-commands can be sync (201) or async (202).** If 202, the Gateway later sends one-or-more PATCH requests to confirm which devices got updated. Clients must handle both paths. *(§5.8 Assigning a calendar to devices, p. 62)*

58. **A group has a `purpose` field** (e.g., `"override"`) — its semantics depend on what services consume the group. Purpose-tagging groups is part of the model. *(§5.4, p. 48)*

59. **Groups can contain other groups as members.** Nested. Watch for cycles. *(§4.3, p. 36; §5.4, p. 48)*

60. **Dynamic control priorities are array-order, not semantic.** Multiple dynamic-control elements active simultaneously resolve by the order they appear in the control program structure. *(§5.8, p. 58)*

61. **Operation effects on dynamic control:** `set | min | max | add | subtract | multiply`. Allows composing commands — e.g., "set to 75% UNLESS already higher" via `max`. *(§5.8, p. 58)*

62. **Active periods are not just absolute times.** Types include: `ActivePeriodAbsolute` (start/end), sunrise-sunset, photocell-triggered, sensor-triggered, value-triggered, `ActivePeriodLightSensor` (with on/off illuminance thresholds). Some require referenced sensors that must already be sent to the Gateway (forward-reference rule). *(§5.5, p. 51–52; §5.8, p. 57, 60)*

63. **Active periods on data loggers serve as alarm suppression.** §5.5 example — disable `communicationFailure` during daytime because the cabinet only has power at night. Failure to read this leads to "always-on" false alarms. *(§5.5 Conditionally disabling data recording, p. 52)*

64. **Calendar rule precedence is JSON-array order**, not semantic specificity. "First Thursday of June" vs "June 3rd" — whichever is listed first wins. Authoring tools must preserve order. *(§5.8 Creating a calendar, p. 61)*

65. **`defaultProgram` on calendars is the fallback.** If no rule matches a given day, the default program applies — otherwise no program runs at all. *(§5.8, p. 61)*

66. **Date conditions support ISO 8601 *partial* dates** (`--10-16` = October 16 every year). Unusual feature. *(§5.8, p. 61)*

67. **By default, actuators are OFF outside active periods.** "an actuator (e.g. generic actuator or lamp actuator) shall be OFF outside the active periods." Explicit fail-safe behaviour. *(§5.8, p. 57)*

---

## F. Logging & Data Collection

68. **Three recording modes — `Vendor` is the default and requires no config.** Many integrators only configure `Periodic` or `Event` and assume nothing happens otherwise; in fact the Gateway is already collecting under `Vendor` rules. This is invisible-by-default behaviour. *(§5.5, p. 49)*

69. **Two endpoints can report log values:**
   - `POST /log-reports` (with logger configuration ref) — typical
   - `POST /devices` (if values relate ONLY to devices' attributes and events) — alternative
   Different semantics for which to use. *(§3.4, p. 18)*

70. **`samplingPeriod` uses ISO 8601 duration format** (`"P1D"` = 1 day). Not seconds. *(§5.5 Periodic recording mode, p. 50)*

71. **The Gateway retries failed log reports at the NEXT SCHEDULED reporting time, not immediately.** "If the REST request is not acknowledged by the CMS, the Gateway shall retransmit it together with any new log records at the next scheduled reporting time." So a single CMS outage delays reports until the next slot, batched. *(§5.5 Reporting log values, p. 53)*

72. **`retryTimes` in logger config bounds the retry count.** Beyond this, entries may be dropped. Failure mode worth designing around. *(§5.5, p. 53)*

73. **`randomTime` in reporting schedule = fleet-wide jitter.** A `randomTime: 60` on times `["09:00:00"]` means each Gateway reports at 09:00:00 ± up to 60 seconds randomly. Prevents thundering-herd at the CMS. Easy to miss and important for scale. *(§5.5 logger-config payloads, p. 49–50)*

74. **Log entries are "successfully reported" only when ACKed by the CMS** — gateways must track per-entry delivery state, not just per-batch. *(§5.5 Reporting log values, p. 53)*

75. **TALQ-defined events come in three flavours:** generic (e.g., `invalidAddress`), service-specific (e.g., `invalidLoggerConfig`), function-specific (e.g., `temperatureTooHigh`). Each may have different routing/handling expectations on the CMS side. *(§5.5 Event recording mode, p. 51)*

---

## G. Smaller Traps

76. **Pagination is optional and must be announced** via `devicePaginationSupported=true` in the configuration service. Without announcement, a CMS using `?offset/limit` may silently get the full list. *(§5.10, p. 63)*

77. **Pagination requires a Gateway-defined ordering** that is "outside TALQ scope." If the Gateway's ordering changes between paginated calls (e.g., device list mutates), you can miss or duplicate devices. No stable cursor specified. *(§5.10, p. 64)*

78. **Pagination metadata is in RESPONSE HEADERS, not the body.** `pagination-offset`, `pagination-defaultLimit`, `pagination-count`, `pagination-totalCount`. Body is just the array of devices. *(§5.10, p. 64)*

79. **Service announcement carries hard limits** the CMS must respect: `maximumCalendars`, `maximumPrograms`, `maxProgramsPerCalendar`, `maxSwitchPointsPerProgram`, `dayOffset` (ControlService), `maximumNumberOfGroups`, `maximumGroupSize` (GroupManagementService). Sending a 31st program to a calendar with `maxProgramsPerCalendar=30` will fail. *(§5.1 step 2 Services announcement, p. 42)*

80. **HTTP response codes are overloaded.** POST can return 200, 201, 202, or 204 depending on context. Firmware update PUT (§5.9) returns **204** specifically. Don't assume "201 = created" universally. *(§3.5 HTTP Response Codes, p. 21; §5.9, p. 63)*

81. **301/307 redirects are explicitly allowed for all methods.** Clients must follow redirects (and preserve method/body on 307). *(§3.5, p. 21)*

82. **`SegmentMonitor.localOverride`** — the device has been overridden manually (e.g., someone flipped a cabinet switch). Important for cabinet-control deployments where the CMS thinks it owns state but doesn't. *(§4.2 Segment Monitor, p. 29)*

83. **`switchingErrorOn`, `switchingErrorOff`, `circuitBreakerTripped`** — segment monitor events for electrical faults. Often the only signal something is wrong upstream. *(§4.2 Segment Monitor, p. 29)*

84. **`cabinetDoorOpen`, `leakageDetected`** — Cabinet Monitor events for physical security/safety. *(§4.2 Cabinet Monitor, p. 32)*

85. **`containerTampered`, `contentsTampered`** — Waste Container events for vandalism detection. *(§4.2 Waste Container, p. 28)*

86. **The Lamp Monitor has BOTH metering AND event attributes around the same physical signals** — `supplyVoltage` (metering) coexists with `supplyVoltageTooHigh` (event). Don't deduplicate. *(§4.2 Lamp Monitor, p. 25)*

87. **Attribute reads include a timestamp in the response body** — `{ "value": 23, "timestamp": "..." }`. §5.7 explicitly says: although the OAS marks timestamp as not required, **it is mandatory for certification**. So OAS-conformant ≠ certifiable. Read this carefully. *(§5.7, p. 56–57)*

88. **An override command without `expiration` runs forever.** "If not specified, there is no expiration." Easy way to leave a fleet stuck at a wrong dim level. *(§5.6, p. 56)*

89. **`rampToLevelTime` and `rampFromLevelTime` are in seconds, with decimals allowed.** Example uses `0.5` and `2.0`. Sub-second ramps are valid. *(§5.6 example payload, p. 55)*

90. **Firmware updates are async, fire-and-forget from the protocol POV.** The CMS PUTs to `/data-packages`; the Gateway returns 204 and then emits events (`changingRelease`, `changeReleaseFailure`, `releaseChanged`, `releaseMismatch`) via the data collection service to indicate progress. No synchronous progress channel. *(§5.9, p. 63)*

91. **The CMS is told NOTHING about firmware install execution details.** "It is not in the scope of TALQ to define the execution process." Per-vendor. *(§5.9, p. 63)*

92. **`releaseMismatch` ≠ `changeReleaseFailure`.** Mismatch = the package was rejected before attempting. Failure = the attempt happened and didn't complete. Two distinct states for monitoring. *(§5.9, p. 63)*

93. **Attribute deletion uses an empty `entity.address` with a present `entity.resource`** — e.g., `entity: { resource: "calendars", address: "" }` un-assigns the calendar. Counter-intuitive un-assign syntax. *(§5.8 Assigning a calendar to devices, p. 62)*

94. **`pH` attribute uses lowercase-p uppercase-H** in `WaterQualitySensor` and `pHSensor`. Casing is meaningful in JSON — don't normalize. *(§4.2 pH Sensor / Water Quality Sensor, p. 28, p. 30)*

95. **`PM10`, `PM2-5`, `PM1` with dashes in names.** `pm2-5HighThreshold` is a valid attribute name. Watch for code generators that mangle dashes. *(§4.2 Particulate Matter sensor, p. 26)*

96. **Plugfest is a real recurring event.** TALQ members run interop sessions. Certification ≠ field-tested. *(§1.5, p. 12)*

97. **TALQ Consortium HQ:** 445 Hoes Lane, Piscataway NJ 08854. Irrelevant for implementation but useful when filing certification paperwork. *(p. 66)*

---

## Quick reference by audience

**If you're implementing a Gateway:** #34, #40, #43, #44, #68, #71, #73, #87.
**If you're implementing a CMS:** #2, #3, #56, #57, #71, #77, #93.
**If you're integrating against an existing TALQ pair:** #1, #20, #22, #24, #80, #88, #92.

---

## Reading order for the OAS files

Per the spec hierarchy, once you've read this document:
1. `[date]-talq-data-model-[ver].json` — type definitions (read first, everything else references it)
2. `[date]-talq-api-gateway-[ver].json` — Gateway-side REST surface
3. `[date]-talq-api-cms-[ver].json` — CMS-side REST surface

In any conflict between this PDF and the above, the OAS files prevail.
