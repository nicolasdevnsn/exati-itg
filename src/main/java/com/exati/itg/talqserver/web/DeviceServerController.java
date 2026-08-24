package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.exati.itg.talqserver.validation.DeviceValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * TALQ gateway server — {@code /devices} tree (CMS → gateway direction).
 * Contract: talq-api-gateway-2-6-3-online.json. Validation semantics:
 * {@link DeviceValidator}.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceServerController {

    private final TalqGatewayStore store;
    private final DeviceValidator validator;

    @GetMapping
    public List<ObjectNode> list(@RequestParam(required = false) String deviceClasses,
                                 @RequestParam(defaultValue = "0") int offset,
                                 @RequestParam(required = false) Integer limit) {
        var filtered = filterByClass(deviceClasses);
        var to = limit == null ? filtered.size() : Math.min(filtered.size(), offset + limit);
        return offset >= filtered.size() ? List.of() : filtered.subList(offset, to);
    }

    @GetMapping("/count")
    public int count(@RequestParam(required = false) String deviceClasses) {
        return filterByClass(deviceClasses).size();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ObjectNode> create(@RequestBody List<ObjectNode> devices) {
        devices.forEach(validator::validateNew);
        devices.forEach(store.getDevices()::insertNew);
        return devices;
    }

    @PutMapping
    public List<ObjectNode> replaceAll(@RequestBody List<ObjectNode> devices) {
        // Bulk PUT is an UPSERT (GW_DV_006): unknown addresses are created,
        // known ones replaced.
        for (var device : devices) {
            var address = store.getDevices().keyOf(device);
            if (store.getDevices().find(address).isPresent()) {
                validator.validatePatch(address, device);
            } else {
                validator.validateUpsert(device);
            }
        }
        for (var device : devices) {
            var address = store.getDevices().keyOf(device);
            if (store.getDevices().find(address).isPresent()) {
                store.getDevices().replaceExisting(address, device);
            } else {
                store.getDevices().insertNew(device);
            }
        }
        return devices;
    }

    @PatchMapping
    public void patchAll(@RequestBody List<ObjectNode> devices) {
        for (var device : devices) {
            var address = store.getDevices().keyOf(device);
            validator.validatePatch(address, device);
            merge(store.getDevices().getOr404(address), device);
        }
    }

    @GetMapping("/{deviceAddress}")
    public ObjectNode get(@PathVariable String deviceAddress) {
        return store.getDevices().getOr404(deviceAddress);
    }

    @PatchMapping("/{deviceAddress}")
    public void patch(@PathVariable String deviceAddress, @RequestBody ObjectNode patch) {
        validator.validatePatch(deviceAddress, patch);
        merge(store.getDevices().getOr404(deviceAddress), patch);
    }

    @DeleteMapping("/{deviceAddress}")
    public ObjectNode delete(@PathVariable String deviceAddress) {
        var device = store.getDevices().getOr404(deviceAddress);
        for (JsonNode fn : device.path("functions")) {
            if ("GatewayFunction".equals(fn.path("type").asText())) {
                // Spec §5.1 resync flow (CMS deletes the gateway → gateway
                // restarts bootstrap) is not implemented yet.
                throw TalqApiException.unprocessable(
                        "deleting the gateway device (resync) is not supported");
            }
        }
        return store.getDevices().delete(deviceAddress);
    }

    @GetMapping("/{deviceAddress}/{functionId}")
    public JsonNode getFunction(@PathVariable String deviceAddress, @PathVariable String functionId) {
        return functionOr404(store.getDevices().getOr404(deviceAddress), functionId);
    }

    @PatchMapping("/{deviceAddress}/{functionId}")
    public void patchFunction(@PathVariable String deviceAddress, @PathVariable String functionId,
                              @RequestBody ObjectNode patch) {
        var device = store.getDevices().getOr404(deviceAddress);
        var function = (ObjectNode) functionOr404(device, functionId);
        var functionType = function.path("type").asText();
        var classFn = validator.classFunctionOr404(validator.classOf(device), functionId);
        var structural = Set.of("id", "type");
        patch.properties().forEach(entry -> {
            if (!structural.contains(entry.getKey())) {
                validator.validateAttribute(functionType, classFn, entry.getKey(), entry.getValue());
            }
        });
        patch.properties().forEach(entry -> {
            if (!structural.contains(entry.getKey())) {
                function.set(entry.getKey(), entry.getValue());
            }
        });
    }

    @GetMapping("/{deviceAddress}/{functionId}/{attributeName}")
    public JsonNode getAttribute(@PathVariable String deviceAddress, @PathVariable String functionId,
                                 @PathVariable String attributeName) {
        var function = functionOr404(store.getDevices().getOr404(deviceAddress), functionId);
        var attribute = function.path(attributeName);
        if (attribute.isMissingNode() || attribute.isNull()) {
            throw TalqApiException.notFound("attribute '" + attributeName
                    + "' has no value on function '" + functionId + "'");
        }
        return attribute;
    }

    @PatchMapping("/{deviceAddress}/{functionId}/{attributeName}")
    public void patchAttribute(@PathVariable String deviceAddress, @PathVariable String functionId,
                               @PathVariable String attributeName, @RequestBody JsonNode wrapper) {
        var device = store.getDevices().getOr404(deviceAddress);
        var function = (ObjectNode) functionOr404(device, functionId);
        var classFn = validator.classFunctionOr404(validator.classOf(device), functionId);
        validator.validateAttribute(function.path("type").asText(), classFn, attributeName, wrapper);
        function.set(attributeName, wrapper);
    }

    private List<ObjectNode> filterByClass(String deviceClassesCsv) {
        var all = store.getDevices().list();
        if (deviceClassesCsv == null || deviceClassesCsv.isBlank()) {
            return all;
        }
        var wanted = Set.copyOf(Arrays.asList(deviceClassesCsv.split(",")));
        return all.stream().filter(d -> wanted.contains(d.path("class").asText())).toList();
    }

    private static JsonNode functionOr404(ObjectNode device, String functionId) {
        for (JsonNode fn : device.path("functions")) {
            if (fn.path("id").asText().equals(functionId)) {
                return fn;
            }
        }
        throw TalqApiException.notFound("function '" + functionId + "' not found on device '"
                + device.path("address").asText() + "'");
    }

    /** Shallow-merge for name/class; functions merged per id, attributes overwritten. */
    private void merge(ObjectNode target, ObjectNode patch) {
        if (patch.has("name")) {
            target.set("name", patch.get("name"));
        }
        if (patch.has("class")) {
            target.set("class", patch.get("class"));
        }
        for (JsonNode patchFn : patch.path("functions")) {
            var functionId = patchFn.path("id").asText();
            var targetFn = (ObjectNode) functionOr404(target, functionId);
            patchFn.properties().forEach(entry -> {
                if (!"id".equals(entry.getKey()) && !"type".equals(entry.getKey())) {
                    targetFn.set(entry.getKey(), entry.getValue());
                }
            });
        }
    }
}
