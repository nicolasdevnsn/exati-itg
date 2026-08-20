package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TALQ gateway server — the five AssetManagement type collections
 * ({@code lamp-types}, {@code luminaire-types}, {@code driver-types},
 * {@code controller-types}, {@code bracket-types}). Identical CRUD contract,
 * so one controller parameterized by the resource segment serves them all.
 */
@RestController
@RequestMapping("/{resource:lamp-types|luminaire-types|driver-types|controller-types|bracket-types}")
@RequiredArgsConstructor
public class AssetTypeServerController {

    private final TalqGatewayStore store;

    @GetMapping
    public List<ObjectNode> list(@PathVariable String resource) {
        return store.assetTypes(resource).list();
    }

    @GetMapping("/count")
    public int count(@PathVariable String resource) {
        return store.assetTypes(resource).count();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ObjectNode> create(@PathVariable String resource,
                                   @RequestBody List<ObjectNode> items) {
        var collection = store.assetTypes(resource);
        items.forEach(item -> validate(resource, item));
        items.forEach(collection::insertNew);
        return items;
    }

    @PutMapping
    public List<ObjectNode> replaceAll(@PathVariable String resource,
                                       @RequestBody List<ObjectNode> items) {
        var collection = store.assetTypes(resource);
        items.forEach(item -> validate(resource, item));
        return items.stream()
                .map(item -> collection.replaceExisting(collection.keyOf(item), item))
                .toList();
    }

    @GetMapping("/{address}")
    public ObjectNode get(@PathVariable String resource, @PathVariable String address) {
        return store.assetTypes(resource).getOr404(address);
    }

    @PutMapping("/{address}")
    public ObjectNode replace(@PathVariable String resource, @PathVariable String address,
                              @RequestBody ObjectNode item) {
        validate(resource, item);
        return store.assetTypes(resource).replaceExisting(address, item);
    }

    @DeleteMapping("/{address}")
    public ObjectNode delete(@PathVariable String resource, @PathVariable String address) {
        return store.assetTypes(resource).delete(address);
    }

    private void validate(String resource, ObjectNode item) {
        if (!item.hasNonNull("address") || !item.hasNonNull("name")) {
            throw TalqApiException.badRequest(resource + " requires 'address' and 'name'");
        }
        if ("lamp-types".equals(resource) && !item.hasNonNull("controlType")) {
            throw TalqApiException.badRequest("lamp type requires 'controlType'");
        }
    }
}
