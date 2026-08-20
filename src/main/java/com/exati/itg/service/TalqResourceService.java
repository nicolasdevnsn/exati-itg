package com.exati.itg.service;

import com.exati.itg.api.dto.talq.DeviceClassDto;
import com.exati.itg.api.dto.talq.DeviceDto;
import com.exati.itg.integration.TalqResourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates Tier 2 TALQ resource operations (device classes, devices)
 * against the Exati IoT Hub. Pass-through for now; the seam for validation,
 * ordering (forward-reference rule) and persistence as more resources land.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TalqResourceService {

    private final TalqResourceClient talqClient;

    public List<DeviceClassDto> createDeviceClasses(List<DeviceClassDto> classes) {
        log.info("Announcing {} device class(es)", classes.size());
        return talqClient.createDeviceClasses(classes);
    }

    public List<DeviceDto> createDevices(List<DeviceDto> devices) {
        log.info("Creating {} device(s)", devices.size());
        return talqClient.createDevices(devices);
    }

    public DeviceDto getDevice(String deviceAddress) {
        return talqClient.getDevice(deviceAddress);
    }

    public List<DeviceDto> listDevices(List<String> deviceClasses, Integer offset, Integer limit) {
        return talqClient.listDevices(deviceClasses, offset, limit);
    }

    public List<DeviceDto> updateDevice(String deviceAddress, DeviceDto device) {
        log.info("Replacing device {}", deviceAddress);
        return talqClient.updateDevice(deviceAddress, device);
    }

    public List<DeviceDto> modifyDevice(String deviceAddress, DeviceDto device) {
        log.info("Modifying device {}", deviceAddress);
        return talqClient.modifyDevice(deviceAddress, device);
    }

    public List<DeviceDto> deleteDevice(String deviceAddress) {
        log.info("Deleting device {}", deviceAddress);
        return talqClient.deleteDevice(deviceAddress);
    }
}
