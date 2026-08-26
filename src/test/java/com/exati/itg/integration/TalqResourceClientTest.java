package com.exati.itg.integration;

import com.exati.itg.api.dto.talq.ClassFunctionDto;
import com.exati.itg.api.dto.talq.DeviceClassDto;
import com.exati.itg.api.dto.talq.DeviceDto;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TalqResourceClientTest {

    private static final String BASE = "http://exati.test";

    private MockRestServiceServer server;
    private TalqResourceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ExatiProperties props = new ExatiProperties(
                BASE, null, null,
                new ExatiProperties.Auth("none", null, null, null),
                new ExatiProperties.Timeout(5_000, 10_000));

        client = new TalqResourceClient(restClient, props, new ObjectMapper());
    }

    @Test
    void createDeviceClasses_success_returnsList() {
        server.expect(requestTo(BASE + "/talq/device-classes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [ { "name": "StreetLightController",
                                    "functions": [ { "functionId": "basic001", "type": "BasicFunction" } ] } ]
                                """));

        List<DeviceClassDto> res = client.createDeviceClasses(List.of(
                new DeviceClassDto("StreetLightController",
                        List.of(new ClassFunctionDto("basic001", "BasicFunction", null, null, null)))));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).name()).isEqualTo("StreetLightController");
        server.verify();
    }

    @Test
    void createDevices_conflict_mapsToConflict() {
        server.expect(requestTo(BASE + "/talq/devices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [ { "key": "resourceConflict", "description": "device already exists" } ]
                                """));

        assertThatThrownBy(() -> client.createDevices(List.of(
                new DeviceDto("uuid-1", "Lamp-1", "StreetLightController", null))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("resourceConflict")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        server.verify();
    }

    @Test
    void getDevice_success_returnsDevice() {
        server.expect(requestTo(BASE + "/talq/devices/uuid-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "address": "uuid-1", "name": "Lamp-1", "class": "StreetLightController" }
                                """));

        DeviceDto res = client.getDevice("uuid-1");

        assertThat(res.address()).isEqualTo("uuid-1");
        assertThat(res.deviceClass()).isEqualTo("StreetLightController");
        server.verify();
    }

    @Test
    void getDevice_notFound_mapsToNotFound() {
        server.expect(requestTo(BASE + "/talq/devices/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[ { \"key\": \"resourceNotFound\", \"description\": \"no such device\" } ]"));

        assertThatThrownBy(() -> client.getDevice("missing"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDevice_referenced_mapsToUnprocessable() {
        server.expect(requestTo(BASE + "/talq/devices/uuid-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[ { \"key\": \"deletingResourceIsReferred\", \"description\": \"in a group\" } ]"));

        assertThatThrownBy(() -> client.deleteDevice("uuid-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("deletingResourceIsReferred")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void updateDevice_success_returnsList() {
        server.expect(requestTo(BASE + "/talq/devices/uuid-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[ { \"address\": \"uuid-1\", \"name\": \"Lamp-1\", \"class\": \"StreetLightController\" } ]"));

        List<DeviceDto> res = client.updateDevice("uuid-1",
                new DeviceDto("uuid-1", "Lamp-1", "StreetLightController", null));

        assertThat(res).hasSize(1);
        server.verify();
    }

    @Test
    void createDeviceClasses_payloadError_mapsToBadRequest() {
        server.expect(requestTo(BASE + "/talq/device-classes"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[ { \"key\": \"payloadError\", \"description\": \"functions is required\" } ]"));

        assertThatThrownBy(() -> client.createDeviceClasses(List.of(
                new DeviceClassDto("X", List.of(new ClassFunctionDto("f1", "BasicFunction", null, null, null))))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
