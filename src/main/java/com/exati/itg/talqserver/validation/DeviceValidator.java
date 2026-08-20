package com.exati.itg.talqserver.validation;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.spec.TalqTypeCatalog;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces the TALQ device rules the certifier's "Validação de Bootstrap"
 * battery exercises (GW_BV_001…011): nil/invalid UUID, duplicate address,
 * class existence, function-id/type consistency with the declared class,
 * exactly one BasicFunction, one gateway per bootstrap, attribute
 * existence/typing/range.
 */
@Component
@RequiredArgsConstructor
public class DeviceValidator {

    public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    /** Function object keys that are structure, not attributes. */
    private static final Set<String> NON_ATTRIBUTE_KEYS = Set.of("id", "type", "vendorAttributes");

    private final TalqGatewayStore store;

    /** Full validation for a device the CMS wants to create (POST /devices). */
    public void validateNew(ObjectNode device) {
        var address = requireText(device, "address");
        if (NIL_UUID.equals(address)) {
            throw TalqApiException.badRequest(
                    "the nil UUID address is reserved for the gateway bootstrap announcement");
        }
        requireUuid(address);
        if (store.getDevices().find(address).isPresent()) {
            throw TalqApiException.conflict("device '" + address + "' already exists");
        }
        requireText(device, "name");
        var deviceClass = classOf(device);
        validateFunctions(device, deviceClass, true);
    }

    /** Validation for a PATCH of an existing device (partial update). */
    public void validatePatch(String pathAddress, ObjectNode patch) {
        var bodyAddress = patch.path("address");
        if (!bodyAddress.isMissingNode() && !bodyAddress.isNull()
                && !bodyAddress.asText().equals(pathAddress)) {
            throw TalqApiException.badRequest(
                    "body 'address' must be absent or equal to the path address");
        }
        var existing = store.getDevices().getOr404(pathAddress);
        var className = patch.has("class") ? patch.path("class").asText()
                : existing.path("class").asText();
        var deviceClass = store.getDeviceClasses().find(className)
                .orElseThrow(() -> TalqApiException.relatedNotFound(
                        "device class '" + className + "' is not declared"));
        if (patch.has("functions")) {
            validateFunctions(patch, deviceClass, false);
        }
    }

    /**
     * Validates one attribute wrapper against the declared class and the spec
     * catalog. Used for function-level and attribute-level PATCHes too.
     */
    public void validateAttribute(String functionType, JsonNode classFunction,
                                  String attributeName, JsonNode wrapper) {
        var declared = classFunction.path("attributes");
        var isDeclared = false;
        for (JsonNode attr : declared) {
            if (attr.path("name").asText().equals(attributeName)) {
                isDeclared = true;
                break;
            }
        }
        if (!isDeclared) {
            // Certifier GW_BV_008 asserts key 'resourceNotFound' here (its own
            // description says relatedResourceNotFound — the assertion wins).
            throw TalqApiException.notFound("attribute '" + attributeName
                    + "' is not declared for function type '" + functionType + "'");
        }
        if (!wrapper.isObject()) {
            throw TalqApiException.badRequest("attribute '" + attributeName
                    + "' must be a typed wrapper object {type, value}");
        }
        var wrapperType = wrapper.path("type").asText();
        // Catalog-independent self-consistency: the certifier (GW_BV_009) sends a
        // correct wrapper with a mismatched JSON value (e.g. a number inside an
        // AttributeString) — including on classes IT announced, which our catalog
        // does not cover. The value must match the wrapper's own declared type.
        var value = wrapper.path("value");
        var valueOk = switch (wrapperType) {
            case "AttributeString", "AttributeUri", "AttributeDateTime" -> value.isTextual();
            case "AttributeBoolean" -> value.isBoolean();
            case "AttributeInteger" -> value.isIntegralNumber();
            case "AttributeFloat" -> value.isNumber();
            default -> true; // structured wrappers (Command, LevelState, …) validated elsewhere
        };
        if (!valueOk) {
            throw TalqApiException.conflict("attribute '" + attributeName + "' of type '"
                    + wrapperType + "' got an incompatible value: " + value.getNodeType());
        }
        var spec = TalqTypeCatalog.attribute(functionType, attributeName).orElse(null);
        if (spec == null) {
            return;
        }
        if (!spec.wrapperType().equals(wrapperType)) {
            throw TalqApiException.conflict("attribute '" + attributeName + "' expects type '"
                    + spec.wrapperType() + "' but got '" + wrapperType + "'");
        }
        if (spec.percent()) {
            var level = value.isNumber() ? value : value.path("level");
            if (level.isNumber() && (level.asDouble() < 0 || level.asDouble() > 100)) {
                throw TalqApiException.unprocessable("attribute '" + attributeName
                        + "' is a percentage and must be within [0, 100]");
            }
        }
    }

    public ObjectNode classOf(ObjectNode device) {
        var className = requireText(device, "class");
        return store.getDeviceClasses().find(className)
                .orElseThrow(() -> TalqApiException.relatedNotFound(
                        "device class '" + className + "' is not declared"));
    }

    /** Finds the class-side FunctionDesc matching a device function id. */
    public JsonNode classFunctionOr404(JsonNode deviceClass, String functionId) {
        for (JsonNode fn : deviceClass.path("functions")) {
            if (fn.path("functionId").asText().equals(functionId)) {
                return fn;
            }
        }
        throw TalqApiException.relatedNotFound("function '" + functionId
                + "' is not declared in device class '" + deviceClass.path("name").asText() + "'");
    }

    private void validateFunctions(ObjectNode device, ObjectNode deviceClass, boolean isNew) {
        var seenIds = new HashSet<String>();
        var basicCount = 0;
        for (JsonNode fnNode : device.path("functions")) {
            var functionId = fnNode.path("id").asText();
            if (functionId.isBlank()) {
                throw TalqApiException.badRequest("every function requires an 'id'");
            }
            if (!seenIds.add(functionId)) {
                throw TalqApiException.badRequest("duplicate function id '" + functionId + "'");
            }
            var classFn = classFunctionOr404(deviceClass, functionId);
            var declaredType = classFn.path("type").asText();
            var actualType = fnNode.path("type").asText();
            if (!declaredType.equals(actualType)) {
                throw TalqApiException.conflict("function '" + functionId + "' is declared as '"
                        + declaredType + "' but the device carries '" + actualType + "'");
            }
            if ("BasicFunction".equals(actualType)) {
                basicCount++;
            }
            if (isNew && "GatewayFunction".equals(actualType)) {
                throw TalqApiException.conflict(
                        "only one gateway per bootstrap: a GatewayFunction device already exists");
            }
            fnNode.properties().forEach(entry -> {
                if (!NON_ATTRIBUTE_KEYS.contains(entry.getKey())) {
                    validateAttribute(actualType, classFn, entry.getKey(), entry.getValue());
                }
            });
        }
        // Every end device must instantiate exactly one BasicFunction when its
        // class declares one (TALQ §4: BasicFunction carries device identity).
        var classDeclaresBasic = false;
        for (JsonNode fn : deviceClass.path("functions")) {
            if ("BasicFunction".equals(fn.path("type").asText())) {
                classDeclaresBasic = true;
                break;
            }
        }
        if (isNew && classDeclaresBasic && basicCount != 1) {
            throw TalqApiException.unprocessable(
                    "a device must instantiate exactly one BasicFunction, got " + basicCount);
        }
    }

    private static String requireText(ObjectNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw TalqApiException.badRequest("required field '" + field + "' is missing");
        }
        return value.asText();
    }

    private static void requireUuid(String address) {
        try {
            UUID.fromString(address);
        } catch (IllegalArgumentException e) {
            throw TalqApiException.badRequest("'" + address + "' is not a valid UUID");
        }
    }
}
