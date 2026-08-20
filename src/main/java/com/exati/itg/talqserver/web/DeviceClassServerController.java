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
 * TALQ gateway server — {@code /device-classes} (read-only on the gateway
 * side: classes are announced BY the gateway TO the CMS, never created here;
 * the official gateway OAS exposes only GET).
 */
@RestController
@RequestMapping("/device-classes")
@RequiredArgsConstructor
public class DeviceClassServerController {

    private final TalqGatewayStore store;

    @GetMapping
    public List<ObjectNode> list() {
        return store.getDeviceClasses().list();
    }

    @GetMapping("/count")
    public int count() {
        return store.getDeviceClasses().count();
    }

    @GetMapping("/{className}")
    public ObjectNode get(@PathVariable String className) {
        return store.getDeviceClasses().getOr404(className);
    }

    @RequestMapping(method = {org.springframework.web.bind.annotation.RequestMethod.POST,
            org.springframework.web.bind.annotation.RequestMethod.PUT,
            org.springframework.web.bind.annotation.RequestMethod.PATCH,
            org.springframework.web.bind.annotation.RequestMethod.DELETE},
            path = {"", "/{className}"})
    public void readOnly() {
        throw TalqApiException.methodNotAllowed(
                "device classes are announced by the gateway; this endpoint is read-only");
    }
}
