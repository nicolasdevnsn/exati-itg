package com.exati.itg.talqserver.store;

import com.exati.itg.talqserver.TalqServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * In-memory registry of everything this gateway exposes to the CMS. Seeded at
 * startup from {@code classpath:talq-seed/*.json} — those files mirror the
 * bootstrap announcements (see {@code homolog/}); if the announcement changes,
 * the seed must change with it, or the certifier's audits will flag the drift.
 *
 * <p>In-memory is deliberate for the homolog phase: the certifier re-runs the
 * bootstrap on every battery, so durable state buys nothing yet. Swap for a
 * JPA-backed store when real ZENIX fleets arrive.
 */
@Component
@Getter
@RequiredArgsConstructor
public class TalqGatewayStore {

    private final ObjectMapper mapper;
    private final TalqServerProperties props;

    private final TalqCollection deviceClasses = new TalqCollection("device class", "name");
    private final TalqCollection devices = new TalqCollection("device", "address");
    private final TalqCollection groups = new TalqCollection("group", "address");
    private final TalqCollection calendars = new TalqCollection("calendar", "id");
    private final TalqCollection controlPrograms = new TalqCollection("control program", "id");
    private final TalqCollection loggerConfigs = new TalqCollection("logger config", "address");
    private final TalqCollection lampTypes = new TalqCollection("lamp type", "address");
    private final TalqCollection luminaireTypes = new TalqCollection("luminaire type", "address");
    private final TalqCollection driverTypes = new TalqCollection("driver type", "address");
    private final TalqCollection controllerTypes = new TalqCollection("controller type", "address");
    private final TalqCollection bracketTypes = new TalqCollection("bracket type", "address");
    /** Pending CMS-issued assignments (calendar/program → devices/groups). */
    private final List<ObjectNode> assignCommands =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private List<ObjectNode> services;

    @PostConstruct
    void seed() {
        loadArray("talq-seed/device-classes.json").forEach(deviceClasses::seed);
        devices.seed(loadObject("talq-seed/gateway-device.json"));
        loadArray("talq-seed/end-devices.json").forEach(devices::seed);
        // Mutable: the certifier's /seed/services harness route replaces it.
        services = java.util.Collections.synchronizedList(
                new java.util.ArrayList<>(loadArray("talq-seed/services.json")));
    }

    public Optional<ObjectNode> findService(String name) {
        return services.stream().filter(s -> s.path("name").asText().equals(name)).findFirst();
    }

    public TalqCollection assetTypes(String resource) {
        return switch (resource) {
            case "lamp-types" -> lampTypes;
            case "luminaire-types" -> luminaireTypes;
            case "driver-types" -> driverTypes;
            case "controller-types" -> controllerTypes;
            case "bracket-types" -> bracketTypes;
            default -> throw new IllegalArgumentException("unknown asset type resource: " + resource);
        };
    }

    private List<ObjectNode> loadArray(String resource) {
        var json = readResource(resource);
        try {
            return List.of(mapper.readValue(json, ObjectNode[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("invalid seed JSON: " + resource, e);
        }
    }

    private ObjectNode loadObject(String resource) {
        var json = readResource(resource);
        try {
            return mapper.readValue(json, ObjectNode.class);
        } catch (IOException e) {
            throw new UncheckedIOException("invalid seed JSON: " + resource, e);
        }
    }

    /** Reads a seed file and substitutes the {@code ${...}} identity placeholders. */
    private String readResource(String resource) {
        try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing seed resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${gatewayAddress}", props.gatewayAddress())
                    .replace("${cmsAddress}", props.cmsAddress())
                    .replace("${gatewayUri}", props.gatewayUri())
                    .replace("${cmsUri}", props.cmsUri())
                    .replace("${vendor}", props.vendor())
                    .replace("${crlUrn}", props.crlUrn());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read seed resource: " + resource, e);
        }
    }
}
