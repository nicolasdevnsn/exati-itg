package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.TalqServerProperties;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * TALQ gateway server — {@code /groups} tree. Enforces the limits this
 * gateway announced in its GroupManagementService (they are a conformance
 * contract; exceeding them must be rejected, not absorbed).
 */
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupServerController {

    private final TalqGatewayStore store;
    private final TalqServerProperties props;

    @GetMapping
    public List<ObjectNode> list() {
        return store.getGroups().list();
    }

    @GetMapping("/count")
    public int count() {
        return store.getGroups().count();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ObjectNode> create(@RequestBody List<ObjectNode> groups) {
        groups.forEach(this::validate);
        if (store.getGroups().count() + groups.size() > props.limits().maximumNumberOfGroups()) {
            throw TalqApiException.unprocessable("announced maximumNumberOfGroups ("
                    + props.limits().maximumNumberOfGroups() + ") would be exceeded");
        }
        groups.forEach(store.getGroups()::insertNew);
        return groups;
    }

    @PutMapping
    public List<ObjectNode> replaceAll(@RequestBody List<ObjectNode> groups) {
        groups.forEach(this::validate);
        return groups.stream()
                .map(g -> store.getGroups().replaceExisting(store.getGroups().keyOf(g), g))
                .toList();
    }

    @GetMapping("/{groupAddress}")
    public ObjectNode get(@PathVariable String groupAddress) {
        return store.getGroups().getOr404(groupAddress);
    }

    @PutMapping("/{groupAddress}")
    public ObjectNode replace(@PathVariable String groupAddress, @RequestBody ObjectNode group) {
        validate(group);
        return store.getGroups().replaceExisting(groupAddress, group);
    }

    @DeleteMapping("/{groupAddress}")
    public ObjectNode delete(@PathVariable String groupAddress) {
        return store.getGroups().delete(groupAddress);
    }

    @PutMapping("/{groupAddress}/members")
    public JsonNode replaceMembers(@PathVariable String groupAddress,
                                   @RequestBody ArrayNode members) {
        var group = store.getGroups().getOr404(groupAddress);
        validateMembers(members);
        group.set("members", members);
        return members;
    }

    @GetMapping("/{groupAddress}/members/count")
    public int membersCount(@PathVariable String groupAddress) {
        return store.getGroups().getOr404(groupAddress).path("members").size();
    }

    @DeleteMapping("/{groupAddress}/members/{memberResource}/{memberAddress}")
    public ObjectNode deleteMember(@PathVariable String groupAddress,
                                   @PathVariable String memberResource,
                                   @PathVariable String memberAddress) {
        var group = store.getGroups().getOr404(groupAddress);
        var members = (ArrayNode) group.path("members");
        for (var i = 0; i < members.size(); i++) {
            var member = members.get(i);
            if (member.path("resource").asText().equals(memberResource)
                    && member.path("address").asText().equals(memberAddress)) {
                return (ObjectNode) members.remove(i);
            }
        }
        throw TalqApiException.notFound("member " + memberResource + "/" + memberAddress
                + " not found in group '" + groupAddress + "'");
    }

    private void validate(ObjectNode group) {
        if (!group.hasNonNull("address") || !group.hasNonNull("ownerCMS")
                || !group.has("members")) {
            throw TalqApiException.badRequest(
                    "group requires 'address', 'ownerCMS' and 'members'");
        }
        validateMembers(group.path("members"));
    }

    private void validateMembers(JsonNode members) {
        if (members.size() > props.limits().maximumGroupSize()) {
            throw TalqApiException.unprocessable("announced maximumGroupSize ("
                    + props.limits().maximumGroupSize() + ") would be exceeded");
        }
        for (JsonNode member : members) {
            var resource = member.path("resource").asText();
            var address = member.path("address").asText();
            if (resource.isBlank() || address.isBlank()) {
                throw TalqApiException.badRequest(
                        "every group member requires 'resource' and 'address'");
            }
            if ("devices".equals(resource) && store.getDevices().find(address).isEmpty()) {
                throw TalqApiException.relatedNotFound(
                        "group member device '" + address + "' does not exist");
            }
        }
    }
}
