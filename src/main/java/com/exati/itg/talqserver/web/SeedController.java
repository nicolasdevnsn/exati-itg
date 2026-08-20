package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.store.TalqCollection;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-harness seeding surface used by the EXATI iotcertifier. NOT part of the
 * TALQ 2.6.3 gateway OAS — discovered empirically on 2026-08-19: before running
 * its battery the certifier POSTs fixtures to {@code /seed/devices},
 * {@code /seed/device-classes} and {@code /seed/services} (no clientAddress,
 * no talqRequestId — hence outside {@link TalqRequestGuard}'s path set).
 *
 * <p>Semantics implemented as upsert: fixtures replace same-keyed entries and
 * are exercised by the battery afterwards. Bodies are logged at INFO so the
 * exact payload shape the certifier uses stays observable.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SeedController {

    private final TalqGatewayStore store;

    @PostMapping("/seed/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode seed(@PathVariable String resource, @RequestBody JsonNode payload) {
        log.info("certifier seed {} <- {}", resource, payload.toString());
        var items = toList(payload);
        if ("services".equals(resource)) {
            store.getServices().clear();
            store.getServices().addAll(items);
            return payload;
        }
        var collection = collectionFor(resource);
        items.forEach(collection::seed);
        return payload;
    }

    private TalqCollection collectionFor(String resource) {
        return switch (resource) {
            case "devices" -> store.getDevices();
            case "device-classes" -> store.getDeviceClasses();
            case "groups" -> store.getGroups();
            case "calendars" -> store.getCalendars();
            case "control-programs" -> store.getControlPrograms();
            case "logger-configs" -> store.getLoggerConfigs();
            case "lamp-types", "luminaire-types", "driver-types",
                 "controller-types", "bracket-types" -> store.assetTypes(resource);
            default -> throw TalqApiException.notFound(
                    "unknown seed resource '" + resource + "'");
        };
    }

    private static List<ObjectNode> toList(JsonNode payload) {
        var items = new ArrayList<ObjectNode>();
        if (payload.isArray()) {
            payload.forEach(node -> items.add((ObjectNode) node));
        } else if (payload.isObject()) {
            items.add((ObjectNode) payload);
        } else {
            throw TalqApiException.badRequest("seed payload must be an object or array");
        }
        return items;
    }
}
