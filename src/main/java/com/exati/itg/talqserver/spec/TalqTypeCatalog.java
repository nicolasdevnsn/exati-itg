package com.exati.itg.talqserver.spec;

import java.util.Map;
import java.util.Optional;

/**
 * Expected attribute wrapper types per TALQ function type, extracted from the
 * official data model (talq-data-model-2-6-3-online.json) for the functions
 * this gateway declares. The certifier PATCHes only attributes the gateway
 * announced, so covering the declared set is sufficient — extend this map when
 * new functions are added to the seed classes.
 */
public final class TalqTypeCatalog {

    /** percent=true → numeric value (or value.level) must sit in [0, 100]. */
    public record AttrSpec(String wrapperType, boolean percent) {
    }

    private static final Map<String, Map<String, AttrSpec>> FUNCTIONS = Map.of(
            "GatewayFunction", Map.ofEntries(
                    Map.entry("cmsUri", new AttrSpec("AttributeUri", false)),
                    Map.entry("cmsAddress", new AttrSpec("AttributeString", false)),
                    Map.entry("gatewayUri", new AttrSpec("AttributeUri", false)),
                    Map.entry("gatewayAddress", new AttrSpec("AttributeString", false)),
                    Map.entry("vendor", new AttrSpec("AttributeString", false)),
                    Map.entry("crlUrn", new AttrSpec("AttributeUri", false)),
                    Map.entry("retryPeriod", new AttrSpec("AttributeFloat", false)),
                    Map.entry("gatewayRetryPeriod", new AttrSpec("AttributeFloat", false)),
                    Map.entry("cmsRetryPeriod", new AttrSpec("AttributeFloat", false)),
                    Map.entry("gatewayNumberOfRetries", new AttrSpec("AttributeInteger", false)),
                    Map.entry("cmsNumberOfRetries", new AttrSpec("AttributeInteger", false))),
            "CommunicationFunction", Map.of(
                    "physicalAddress", new AttrSpec("AttributeString", false),
                    "communicationFailure", new AttrSpec("AttributeBoolean", false),
                    "communicationType", new AttrSpec("AttributeString", false)),
            "BasicFunction", Map.of(
                    "swVersion", new AttrSpec("AttributeString", false),
                    "deviceReset", new AttrSpec("AttributeBoolean", false),
                    "currentTime", new AttrSpec("AttributeDateTime", false)),
            "LampActuatorFunction", Map.of(
                    "defaultLightState", new AttrSpec("AttributeLevelState", true),
                    "targetLightCommand", new AttrSpec("AttributeCommand", false),
                    "feedbackLightCommand", new AttrSpec("AttributeCommand", false),
                    "actualLightState", new AttrSpec("AttributeLevelState", true),
                    "calendarID", new AttrSpec("AttributeString", false),
                    "lightStateChange", new AttrSpec("AttributeBoolean", false)),
            "LampMonitorFunction", Map.of(
                    "lampFailure", new AttrSpec("AttributeBoolean", false),
                    "operatingHours", new AttrSpec("AttributeFloat", false),
                    "switchOnCounter", new AttrSpec("AttributeInteger", false)),
            "BatteryLevelSensorFunction", Map.of(
                    "batteryLevel", new AttrSpec("AttributeFloat", true),
                    "batteryLevelLowThreshold", new AttrSpec("AttributeFloat", true),
                    "batteryLevelTooLow", new AttrSpec("AttributeBoolean", false)),
            "TemperatureSensorFunction", Map.of(
                    "temperature", new AttrSpec("AttributeFloat", false),
                    "temperatureHighThreshold", new AttrSpec("AttributeFloat", false)),
            "ElectricalMeterFunction", Map.of(
                    "totalPower", new AttrSpec("AttributeFloat", false),
                    "totalActiveEnergy", new AttrSpec("AttributeFloat", false)));

    private TalqTypeCatalog() {
    }

    public static Optional<AttrSpec> attribute(String functionType, String attributeName) {
        return Optional.ofNullable(FUNCTIONS.get(functionType))
                .map(attrs -> attrs.get(attributeName));
    }

    public static boolean knowsFunction(String functionType) {
        return FUNCTIONS.containsKey(functionType);
    }
}
