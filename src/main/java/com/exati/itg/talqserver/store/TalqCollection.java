package com.exati.itg.talqserver.store;

import com.exati.itg.talqserver.TalqApiException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One addressable TALQ resource collection (groups, calendars, lamp-types…).
 * Key field varies per resource: {@code address} for most, {@code id} for
 * calendars/control-programs, {@code name} for device classes.
 *
 * <p>All access is synchronized on the instance — collections are small
 * (certifier scale) and correctness beats throughput here.
 */
public class TalqCollection {

    private final String resourceName;
    private final String keyField;
    private final Map<String, ObjectNode> items = new LinkedHashMap<>();

    public TalqCollection(String resourceName, String keyField) {
        this.resourceName = resourceName;
        this.keyField = keyField;
    }

    public String keyField() {
        return keyField;
    }

    public String keyOf(ObjectNode item) {
        var key = item.path(keyField);
        if (key.isMissingNode() || key.isNull() || key.asText().isBlank()) {
            throw TalqApiException.badRequest(
                    resourceName + ": required field '" + keyField + "' is missing");
        }
        return key.asText();
    }

    public synchronized List<ObjectNode> list() {
        return new ArrayList<>(items.values());
    }

    public synchronized int count() {
        return items.size();
    }

    public synchronized Optional<ObjectNode> find(String key) {
        return Optional.ofNullable(items.get(key));
    }

    public synchronized ObjectNode getOr404(String key) {
        var item = items.get(key);
        if (item == null) {
            throw TalqApiException.notFound(resourceName + " '" + key + "' not found");
        }
        return item;
    }

    public synchronized void insertNew(ObjectNode item) {
        var key = keyOf(item);
        if (items.containsKey(key)) {
            throw TalqApiException.conflict(resourceName + " '" + key + "' already exists");
        }
        items.put(key, item);
    }

    public synchronized ObjectNode replaceExisting(String key, ObjectNode item) {
        if (!items.containsKey(key)) {
            throw TalqApiException.notFound(resourceName + " '" + key + "' not found");
        }
        item.put(keyField, key);
        items.put(key, item);
        return item;
    }

    public synchronized ObjectNode delete(String key) {
        var removed = items.remove(key);
        if (removed == null) {
            throw TalqApiException.notFound(resourceName + " '" + key + "' not found");
        }
        return removed;
    }

    /** Seed entry — bypasses the duplicate check, used only at startup. */
    public synchronized void seed(ObjectNode item) {
        items.put(keyOf(item), item);
    }
}
