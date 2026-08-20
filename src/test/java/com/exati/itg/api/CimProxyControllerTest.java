package com.exati.itg.api;

import com.exati.itg.config.CimProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CimProxyControllerTest {

    private static final String CIM_BASE = "http://cim.test/ami/cim";

    private MockRestServiceServer server;
    private CimProxyController controller;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        controller = new CimProxyController(client, new CimProperties(CIM_BASE, new CimProperties.Timeout(5_000, 30_000)));
    }

    @Test
    void forwardsPost_withBodyQueryAndAuthHeader() {
        server.expect(requestTo(CIM_BASE + "/V2/meterread?meterNo=123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer cim-token"))
                .andExpect(content().string("{\"meterNo\":\"123\"}"))
                .andRespond(withSuccess("{\"taskId\":\"t-1\"}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cim/V2/meterread");
        request.setQueryString("meterNo=123");
        request.addHeader("Authorization", "Bearer cim-token");
        request.addHeader("Content-Type", "application/json");

        ResponseEntity<byte[]> res = controller.proxy(request, "{\"meterNo\":\"123\"}".getBytes());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(res.getBody())).contains("t-1");
        server.verify();
    }

    @Test
    void passesThroughUpstreamErrorStatus_withoutTranslation() {
        // A 404 from CIM must reach the caller as 404 with the original body — not remapped.
        server.expect(requestTo(CIM_BASE + "/udil/on_demand_data_read"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"meter not found\"}"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cim/udil/on_demand_data_read");

        ResponseEntity<byte[]> res = controller.proxy(request, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new String(res.getBody())).contains("meter not found");
        server.verify();
    }

    @Test
    void forwardsGet_noBody_deepPath() {
        server.expect(requestTo(CIM_BASE + "/V1/code/findCimCodeById?id=7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cim/V1/code/findCimCodeById");
        request.setQueryString("id=7");

        ResponseEntity<byte[]> res = controller.proxy(request, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(res.getBody())).contains("\"id\":7");
        server.verify();
    }
}
