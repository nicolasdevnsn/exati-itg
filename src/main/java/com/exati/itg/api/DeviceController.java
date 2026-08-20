package com.exati.itg.api;

import com.exati.itg.api.dto.talq.DeviceDto;
import com.exati.itg.service.TalqResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Edge for TALQ Tier 2 devices. Forwards to the Exati IoT Hub {@code /talq/devices}
 * API. JWT-protected like the rest of the API.
 *
 * <p>Per the TALQ spec, PUT is destructive on the function array (full replace)
 * while PATCH adds-or-updates by function id and cannot delete functions.
 */
@RestController
@RequestMapping("/api/v1/talq/devices")
@RequiredArgsConstructor
@Validated
@Tag(name = "TALQ Devices", description = "Create, read, update and delete Tier 2 devices on the Exati IoT Hub")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final TalqResourceService talqResourceService;

    @PostMapping
    @Operation(summary = "Create one or more TALQ devices")
    public ResponseEntity<List<DeviceDto>> create(
            @RequestBody @NotEmpty List<@Valid DeviceDto> devices) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(talqResourceService.createDevices(devices));
    }

    @GetMapping("/{deviceAddress}")
    @Operation(summary = "Get a single TALQ device by address")
    public DeviceDto get(@PathVariable String deviceAddress) {
        return talqResourceService.getDevice(deviceAddress);
    }

    @GetMapping
    @Operation(summary = "List TALQ devices, optionally filtered by class and paginated")
    public List<DeviceDto> list(
            @RequestParam(required = false) List<String> deviceClasses,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        return talqResourceService.listDevices(deviceClasses, offset, limit);
    }

    @PutMapping("/{deviceAddress}")
    @Operation(summary = "Full replace of a TALQ device (destructive on the function array)")
    public List<DeviceDto> update(
            @PathVariable String deviceAddress,
            @RequestBody @Valid DeviceDto device) {
        return talqResourceService.updateDevice(deviceAddress, device);
    }

    @PatchMapping("/{deviceAddress}")
    @Operation(summary = "Add-or-update a TALQ device by function id (cannot delete functions)")
    public List<DeviceDto> modify(
            @PathVariable String deviceAddress,
            @RequestBody @Valid DeviceDto device) {
        return talqResourceService.modifyDevice(deviceAddress, device);
    }

    @DeleteMapping("/{deviceAddress}")
    @Operation(summary = "Delete a TALQ device by address")
    public List<DeviceDto> delete(@PathVariable String deviceAddress) {
        return talqResourceService.deleteDevice(deviceAddress);
    }
}
