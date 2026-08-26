package com.exati.itg.integration;

import com.exati.itg.api.dto.talq.DeviceClassDto;
import com.exati.itg.api.dto.talq.DeviceDto;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Client for the Exati IoT Hub TALQ <b>Tier&nbsp;2</b> (resource) API — device
 * classes, devices, etc. Distinct from {@link ExatiTicketsClient} (Solicitações):
 * payloads are arrays, and errors come back as an array of
 * {@link TalqErrorMessage} rather than a single object.
 *
 * <p><b>DEPRECATED</b>: hardcoded to the staging paths Exati told us to ignore;
 * kept only until formally removed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TalqResourceClient {

    private static final String DEVICE_CLASSES_PATH = "/talq/device-classes";
    private static final String DEVICES_PATH = "/talq/devices";

    private static final ParameterizedTypeReference<List<DeviceClassDto>> DEVICE_CLASS_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<DeviceDto>> DEVICE_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient exatiRestClient;
    private final ExatiProperties props;
    private final ObjectMapper objectMapper;

    /** Announce device classes (POST /talq/device-classes). */
    public List<DeviceClassDto> createDeviceClasses(List<DeviceClassDto> classes) {
        return call(() -> exatiRestClient.post()
                .uri(b -> b.path(DEVICE_CLASSES_PATH)
                        .queryParamIfPresent("clientAddress", clientAddress())
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(classes)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_CLASS_LIST));
    }

    /** Create devices (POST /talq/devices). */
    public List<DeviceDto> createDevices(List<DeviceDto> devices) {
        return call(() -> exatiRestClient.post()
                .uri(b -> b.path(DEVICES_PATH)
                        .queryParamIfPresent("clientAddress", clientAddress())
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(devices)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_LIST));
    }

    /** Get a single device (GET /talq/devices/{deviceAddress}). */
    public DeviceDto getDevice(String deviceAddress) {
        return call(() -> exatiRestClient.get()
                .uri(b -> b.path(DEVICES_PATH + "/{deviceAddress}")
                        .queryParamIfPresent("clientAddress", clientAddress())
                        .build(deviceAddress))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DeviceDto.class));
    }

    /** List devices (GET /talq/devices), optionally filtered/paginated. */
    public List<DeviceDto> listDevices(List<String> deviceClasses, Integer offset, Integer limit) {
        return call(() -> exatiRestClient.get()
                .uri(b -> {
                    b.path(DEVICES_PATH).queryParamIfPresent("clientAddress", clientAddress());
                    if (deviceClasses != null && !deviceClasses.isEmpty()) {
                        b.queryParam("deviceClasses", deviceClasses.toArray());
                    }
                    if (offset != null) {
                        b.queryParam("offset", offset);
                    }
                    if (limit != null) {
                        b.queryParam("limit", limit);
                    }
                    return b.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_LIST));
    }

    /** Full replace of a device (PUT /talq/devices/{deviceAddress}) — destructive on the function array. */
    public List<DeviceDto> updateDevice(String deviceAddress, DeviceDto device) {
        return call(() -> exatiRestClient.put()
                .uri(b -> b.path(DEVICES_PATH + "/{deviceAddress}")
                        .queryParamIfPresent("clientAddress", clientAddress())
                        .build(deviceAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .body(device)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_LIST));
    }

    /** Add-or-update a device by function id (PATCH /talq/devices/{deviceAddress}) — cannot delete functions. */
    public List<DeviceDto> modifyDevice(String deviceAddress, DeviceDto device) {
        return call(() -> exatiRestClient.patch()
                .uri(DEVICES_PATH + "/{deviceAddress}", deviceAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .body(device)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_LIST));
    }

    /** Delete a device (DELETE /talq/devices/{deviceAddress}). */
    public List<DeviceDto> deleteDevice(String deviceAddress) {
        return call(() -> exatiRestClient.method(HttpMethod.DELETE)
                .uri(b -> b.path(DEVICES_PATH + "/{deviceAddress}")
                        .queryParamIfPresent("clientAddress", clientAddress())
                        .build(deviceAddress))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(DEVICE_LIST));
    }

    private Optional<String> clientAddress() {
        return StringUtils.hasText(props.clientAddress()) ? Optional.of(props.clientAddress()) : Optional.empty();
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException ex) {
            log.error("Exati Tier 2 unreachable at {}", props.baseUrl(), ex);
            throw ApiException.badGateway("Exati IoT Hub is unreachable.");
        }
    }

    /**
     * Maps a Tier 2 error (array of {@link TalqErrorMessage}) onto an
     * {@link ApiException}, keyed off the HTTP status with the TALQ error keys
     * and descriptions joined into the detail.
     */
    private ApiException translate(ClientHttpResponse res) throws IOException {
        HttpStatusCode status = res.getStatusCode();
        List<TalqErrorMessage> messages = parse(res);
        String detail = (messages == null || messages.isEmpty())
                ? "TALQ request failed (HTTP " + status.value() + ")."
                : messages.stream()
                        .map(m -> m.description() != null ? m.key() + ": " + m.description() : m.key())
                        .collect(Collectors.joining("; "));

        log.warn("Exati Tier 2 error: http={} detail={}", status.value(), detail);

        if (status.is5xxServerError()) {
            return ApiException.badGateway("Exati IoT Hub error: " + detail);
        }
        return switch (status.value()) {
            case 400 -> ApiException.badRequest(detail);
            case 403 -> ApiException.forbidden(detail);
            case 404 -> ApiException.notFound(detail);
            case 409 -> ApiException.conflict(detail);
            default -> ApiException.unprocessable(detail);
        };
    }

    private List<TalqErrorMessage> parse(ClientHttpResponse res) {
        try {
            byte[] body = res.getBody().readAllBytes();
            if (body.length == 0) {
                return List.of();
            }
            return objectMapper.readValue(body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TalqErrorMessage.class));
        } catch (IOException e) {
            return List.of();
        }
    }
}
