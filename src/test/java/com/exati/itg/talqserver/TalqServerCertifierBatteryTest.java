package com.exati.itg.talqserver;

import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.exati.itg.talqserver.validation.DeviceValidator;
import com.exati.itg.talqserver.web.AssetTypeServerController;
import com.exati.itg.talqserver.web.ControlServerController;
import com.exati.itg.talqserver.web.DataCollectServerController;
import com.exati.itg.talqserver.web.DeviceClassServerController;
import com.exati.itg.talqserver.web.DeviceServerController;
import com.exati.itg.talqserver.web.GroupServerController;
import com.exati.itg.talqserver.web.ServiceServerController;
import com.exati.itg.talqserver.web.TalqRequestGuard;
import com.exati.itg.talqserver.web.TalqServerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mirrors the iotcertifier "Validação de Bootstrap" battery (GW_BV_001…011)
 * plus the read-only and cross-cutting header/param rules, so regressions are
 * caught before the certifier sees them.
 */
class TalqServerCertifierBatteryTest {

    private static final String CMS = "10000000-0000-0000-0000-000000000001";
    private static final String GW = "81eaf17a-c10e-4a98-a353-7641333d6c51";
    private static final String NIL = "00000000-0000-0000-0000-000000000000";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        var props = new TalqServerProperties(GW, CMS,
                "https://gateway-homolog.nansen.com.br:8443/",
                "https://iotcertifier.exati.com.br:8443/cms/token/",
                "Nansen", "https://gateway-homolog.nansen.com.br:8443/crl/ca.crl",
                new TalqServerProperties.Limits(10, 20, 10, 10, 4, 50, 500, 10));
        var store = new TalqGatewayStore(mapper, props);
        ReflectionTestUtils.invokeMethod(store, "seed");
        var validator = new DeviceValidator(store);
        mvc = MockMvcBuilders.standaloneSetup(
                        new DeviceServerController(store, validator),
                        new DeviceClassServerController(store),
                        new ServiceServerController(store),
                        new GroupServerController(store, props),
                        new ControlServerController(store, props),
                        new AssetTypeServerController(store),
                        new DataCollectServerController(store, props, mapper))
                .setControllerAdvice(new TalqServerAdvice())
                .addInterceptors(new TalqRequestGuard(props))
                .build();
    }

    private MockHttpServletRequestBuilder talq(MockHttpServletRequestBuilder builder) {
        return builder.header("talq-api-version", "2.6.0")
                .queryParam("clientAddress", CMS)
                .queryParam("talqRequestId", UUID.randomUUID().toString())
                .contentType("application/json");
    }

    private static String zenix(String address) {
        return """
                [{"address":"%s","name":"ZENIX teste","class":"NansenZenixClass",
                  "functions":[
                    {"id":"basic-01","type":"BasicFunction",
                     "swVersion":{"type":"AttributeString","value":"1.0.0"}},
                    {"id":"lamp-01","type":"LampActuatorFunction",
                     "actualLightState":{"type":"AttributeLevelState","value":100}},
                    {"id":"meter-01","type":"ElectricalMeterFunction"}
                  ]}]""".formatted(address);
    }

    @Test
    @DisplayName("GW_BV_001 — nil-UUID device is rejected with payloadError")
    void nilUuidRejected() throws Exception {
        mvc.perform(talq(post("/devices")).content(zenix(NIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("payloadError"));
    }

    @Test
    @DisplayName("GW_BV_002/011 — duplicate device address answers 409 resourceConflict")
    void duplicateAddressRejected() throws Exception {
        var address = UUID.randomUUID().toString();
        mvc.perform(talq(post("/devices")).content(zenix(address)))
                .andExpect(status().isCreated());
        mvc.perform(talq(post("/devices")).content(zenix(address)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$[0].key").value("resourceConflict"));
    }

    @Test
    @DisplayName("GW_BV_003 — a device without its BasicFunction is rejected")
    void zeroBasicFunctionsRejected() throws Exception {
        var body = """
                [{"address":"%s","name":"ZENIX","class":"NansenZenixClass",
                  "functions":[{"id":"lamp-01","type":"LampActuatorFunction"}]}]"""
                .formatted(UUID.randomUUID());
        mvc.perform(talq(post("/devices")).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GW_BV_004 — a second GatewayFunction device is rejected")
    void secondGatewayRejected() throws Exception {
        var body = """
                [{"address":"%s","name":"clone","class":"NansenGatewayClass",
                  "functions":[{"id":"gateway-function-01","type":"GatewayFunction"}]}]"""
                .formatted(UUID.randomUUID());
        mvc.perform(talq(post("/devices")).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GW_BV_005 — unknown device class answers 404 relatedResourceNotFound")
    void unknownClassRejected() throws Exception {
        var body = """
                [{"address":"%s","name":"x","class":"NoSuchClass","functions":[]}]"""
                .formatted(UUID.randomUUID());
        mvc.perform(talq(post("/devices")).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0].key").value("relatedResourceNotFound"));
    }

    @Test
    @DisplayName("GW_BV_006 — function id not declared in the class answers 404")
    void unknownFunctionIdRejected() throws Exception {
        var body = """
                [{"address":"%s","name":"x","class":"NansenZenixClass",
                  "functions":[{"id":"basic-01","type":"BasicFunction"},
                               {"id":"ghost-99","type":"BasicFunction"}]}]"""
                .formatted(UUID.randomUUID());
        mvc.perform(talq(post("/devices")).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0].key").value("relatedResourceNotFound"));
    }

    @Test
    @DisplayName("GW_BV_007 — function type differing from the class answers 409")
    void functionTypeMismatchRejected() throws Exception {
        var body = """
                [{"address":"%s","name":"x","class":"NansenZenixClass",
                  "functions":[{"id":"basic-01","type":"LampActuatorFunction"}]}]"""
                .formatted(UUID.randomUUID());
        mvc.perform(talq(post("/devices")).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GW_BV_008 — PATCH of an undeclared attribute answers 404 resourceNotFound")
    void undeclaredAttributeRejected() throws Exception {
        mvc.perform(talq(patch("/devices/" + GW + "/gateway-function-01/noSuchAttribute"))
                        .content("{\"type\":\"AttributeString\",\"value\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0].key").value("resourceNotFound"));
    }

    @Test
    @DisplayName("GW_BV_009 — PATCH with the wrong wrapper type answers 409")
    void attributeTypeMismatchRejected() throws Exception {
        mvc.perform(talq(patch("/devices/" + GW + "/gateway-function-01/vendor"))
                        .content("{\"type\":\"AttributeFloat\",\"value\":1.0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$[0].key").value("resourceConflict"));
    }

    @Test
    @DisplayName("GW_BV_010 — percent attribute out of [0,100] answers 422")
    void percentOutOfRangeRejected() throws Exception {
        var address = UUID.randomUUID().toString();
        mvc.perform(talq(post("/devices")).content(zenix(address)))
                .andExpect(status().isCreated());
        mvc.perform(talq(patch("/devices/" + address + "/lamp-01/actualLightState"))
                        .content("{\"type\":\"AttributeLevelState\",\"value\":101}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PATCH body address must match the path address")
    void patchAddressMismatchRejected() throws Exception {
        mvc.perform(talq(patch("/devices/" + GW))
                        .content("{\"address\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Read-only endpoints answer 405 methodNotAllowed")
    void readOnlyEndpoints() throws Exception {
        mvc.perform(talq(post("/device-classes")).content("[]"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$[0].key").value("methodNotAllowed"));
        mvc.perform(talq(post("/services")).content("[]"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GW_PE_004/005/006/007 — parameter error keys per certifier contract")
    void guardRules() throws Exception {
        mvc.perform(get("/devices").queryParam("clientAddress", CMS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterMissing"));
        mvc.perform(get("/devices").header("talq-api-version", "2.6.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterMissing"));
        mvc.perform(get("/devices").header("talq-api-version", "2.6.0")
                        .queryParam("clientAddress", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterValueNotValid"));
        mvc.perform(post("/devices").header("talq-api-version", "2.6.0")
                        .queryParam("clientAddress", CMS)
                        .contentType("application/json").content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterMissing"));
        mvc.perform(post("/devices").header("talq-api-version", "2.6.0")
                        .queryParam("clientAddress", CMS)
                        .queryParam("talqRequestId", "not-a-uuid")
                        .contentType("application/json").content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterValueNotValid"));
        mvc.perform(post("/devices").header("talq-api-version", "2.6.0")
                        .queryParam("clientAddress", CMS)
                        .queryParam("talqRequestId", NIL)
                        .contentType("application/json").content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].key").value("parameterValueNotValid"));
    }

    @Test
    @DisplayName("GW_DV_005 — typeless PATCH accepted even with unexpected value kind")
    void typelessPatchNeverConflicts() throws Exception {
        mvc.perform(talq(patch("/devices/" + GW + "/gateway-function-01/vendor"))
                        .content("{\"value\":42}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Announced state is served: gateway device, classes, services")
    void announcedStateServed() throws Exception {
        mvc.perform(talq(get("/devices/" + GW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.class").value("NansenGatewayClass"));
        mvc.perform(talq(get("/device-classes/count")))
                .andExpect(status().isOk());
        mvc.perform(talq(get("/services/ControlService")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maximumCalendars").value(10));
        mvc.perform(talq(get("/services/DataPackageService")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleting the gateway device (resync) is refused")
    void gatewayDeleteRefused() throws Exception {
        mvc.perform(talq(delete("/devices/" + GW)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GW_DV_005 — typeless PATCH accepted; integral float accepted; 5.5 conflicts")
    void attributePatchTolerance() throws Exception {
        mvc.perform(talq(patch("/devices/" + GW + "/gateway-function-01/gatewayNumberOfRetries"))
                        .content("{\"value\":5.0}"))
                .andExpect(status().isOk());
        mvc.perform(talq(patch("/devices/" + GW + "/gateway-function-01/gatewayNumberOfRetries"))
                        .content("{\"type\":\"AttributeInteger\",\"value\":5.5}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GW_DV_006 — bulk PUT upserts unknown devices")
    void bulkPutUpserts() throws Exception {
        var a1 = UUID.randomUUID();
        var a2 = UUID.randomUUID();
        var body = """
                [{"address":"%s","name":"put-1","class":"NansenZenixClass","functions":[]},
                 {"address":"%s","name":"put-2","class":"NansenZenixClass","functions":[]}]"""
                .formatted(a1, a2);
        mvc.perform(talq(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/devices")).content(body))
                .andExpect(status().isOk());
        mvc.perform(talq(get("/devices/" + a1))).andExpect(status().isOk());
        mvc.perform(talq(get("/devices/" + a2))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GW_ER_004 — calendar rule without 'program' answers 422 with TALQ error array")
    void calendarRuleWithoutProgramRejected() throws Exception {
        var body = """
                [{"id":"%s","ownerCMS":"%s","rules":[{"startDate":"2026-01-01"}]}]"""
                .formatted(UUID.randomUUID(), CMS);
        mvc.perform(talq(post("/calendars")).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$[0].key").value("payloadError"));
    }

    @Test
    @DisplayName("Groups: member must exist; limits enforced by announcement")
    void groupRules() throws Exception {
        var body = """
                [{"address":"%s","ownerCMS":"%s","members":[
                   {"resource":"devices","address":"%s"}]}]"""
                .formatted(UUID.randomUUID(), CMS, UUID.randomUUID());
        mvc.perform(talq(post("/groups")).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0].key").value("relatedResourceNotFound"));
    }
}
