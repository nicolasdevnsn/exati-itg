package com.exati.itg.api;

import com.exati.itg.api.dto.talq.DeviceClassDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Edge for TALQ Tier 2 device classes. Forwards to Exati
 * {@code POST /talq/device-classes}. JWT-protected like the rest of the API.
 */
@RestController
@RequestMapping("/api/v1/talq/device-classes")
@RequiredArgsConstructor
@Validated
@Tag(name = "TALQ Device Classes", description = "Announce Tier 2 device classes to the Exati IoT Hub")
@SecurityRequirement(name = "bearerAuth")
public class DeviceClassController {

    private final TalqResourceService talqResourceService;

    @PostMapping
    @Operation(summary = "Announce one or more TALQ device classes")
    public ResponseEntity<List<DeviceClassDto>> create(
            @RequestBody @NotEmpty List<@Valid DeviceClassDto> deviceClasses) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(talqResourceService.createDeviceClasses(deviceClasses));
    }
}
