package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.TalqServerProperties;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TALQ gateway server — DataCollectService surface: {@code /logger-configs},
 * {@code /log-reports}, {@code /data-packages}.
 *
 * <p>{@code /data-packages} answers 404: DataPackageService was deliberately
 * NOT announced by this gateway (firmware distribution is out of scope), and
 * per the spec unannounced services are not served.
 */
@RestController
@RequiredArgsConstructor
public class DataCollectServerController {

    private final TalqGatewayStore store;
    private final TalqServerProperties props;
    private final ObjectMapper mapper;

    // ── /logger-configs ─────────────────────────────────────────────────

    @PostMapping("/logger-configs")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody ObjectNode config) {
        validate(config);
        if (store.getLoggerConfigs().count() >= props.limits().maximumDataLogs()) {
            throw TalqApiException.unprocessable("announced maximumDataLogs ("
                    + props.limits().maximumDataLogs() + ") would be exceeded");
        }
        store.getLoggerConfigs().insertNew(config);
    }

    @GetMapping("/logger-configs/{loggerAddress}")
    public ObjectNode get(@PathVariable String loggerAddress) {
        return store.getLoggerConfigs().getOr404(loggerAddress);
    }

    @PutMapping("/logger-configs/{loggerAddress}")
    public void replace(@PathVariable String loggerAddress, @RequestBody ObjectNode config) {
        validate(config);
        store.getLoggerConfigs().replaceExisting(loggerAddress, config);
    }

    @DeleteMapping("/logger-configs/{loggerAddress}")
    public ObjectNode delete(@PathVariable String loggerAddress) {
        return store.getLoggerConfigs().delete(loggerAddress);
    }

    // ── /log-reports ────────────────────────────────────────────────────

    @GetMapping("/log-reports/count")
    public int reportCount() {
        return store.getLoggerConfigs().count();
    }

    @GetMapping("/log-reports/{loggerAddress}")
    public ObjectNode report(@PathVariable String loggerAddress) {
        store.getLoggerConfigs().getOr404(loggerAddress);
        // No samples collected yet — an empty report is valid; entries fill in
        // once the ami-cim measurement bridge lands.
        var report = mapper.createObjectNode();
        report.put("address", loggerAddress);
        report.set("entries", mapper.createArrayNode());
        return report;
    }

    // ── /data-packages (service not announced) ──────────────────────────

    @GetMapping("/data-packages")
    public List<ObjectNode> dataPackages() {
        throw TalqApiException.notFound("DataPackageService is not announced by this gateway");
    }

    @PutMapping("/data-packages")
    public void putDataPackages(@RequestBody JsonNode ignored) {
        throw TalqApiException.notFound("DataPackageService is not announced by this gateway");
    }

    private void validate(ObjectNode config) {
        if (!config.hasNonNull("address") || !config.hasNonNull("ownerCMS")) {
            throw TalqApiException.badRequest("logger config requires 'address' and 'ownerCMS'");
        }
        for (JsonNode source : config.path("sourceAddresses")) {
            var address = source.isTextual() ? source.asText() : source.path("address").asText();
            if (!address.isBlank() && store.getDevices().find(address).isEmpty()) {
                throw TalqApiException.relatedNotFound(
                        "logger source device '" + address + "' does not exist");
            }
        }
    }
}
