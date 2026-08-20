package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TALQ gateway server — {@code /services} (read-only: services are announced
 * by the gateway during bootstrap; the CMS may only consult them here).
 */
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceServerController {

    private final TalqGatewayStore store;

    @GetMapping
    public List<ObjectNode> list() {
        return store.getServices();
    }

    @GetMapping("/{serviceName}")
    public ObjectNode get(@PathVariable String serviceName) {
        return store.findService(serviceName)
                .orElseThrow(() -> TalqApiException.notFound(
                        "service '" + serviceName + "' is not announced by this gateway"));
    }

    @RequestMapping(method = {org.springframework.web.bind.annotation.RequestMethod.POST,
            org.springframework.web.bind.annotation.RequestMethod.PUT,
            org.springframework.web.bind.annotation.RequestMethod.PATCH,
            org.springframework.web.bind.annotation.RequestMethod.DELETE},
            path = {"", "/{serviceName}"})
    public void readOnly() {
        throw TalqApiException.methodNotAllowed(
                "services are announced by the gateway; this endpoint is read-only");
    }
}
