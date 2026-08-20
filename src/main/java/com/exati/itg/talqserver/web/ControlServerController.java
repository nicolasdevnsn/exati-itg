package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.TalqServerProperties;
import com.exati.itg.talqserver.store.TalqCollection;
import com.exati.itg.talqserver.store.TalqGatewayStore;
import com.fasterxml.jackson.databind.JsonNode;
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
 * TALQ gateway server — ControlService surface: {@code /calendars},
 * {@code /control-programs}, {@code /assign-commands},
 * {@code /override-commands}. Announced ControlService limits are enforced.
 *
 * <p>TODO(ami-cim): assign/override commands currently update in-memory state
 * only; the actuation bridge (relay on/off via {@code ops_*_switch_relay}
 * through ami-cim) plugs in here once device-to-meter mapping lands.
 */
@RestController
@RequiredArgsConstructor
public class ControlServerController {

    private final TalqGatewayStore store;
    private final TalqServerProperties props;

    // ── /calendars ──────────────────────────────────────────────────────

    @GetMapping("/calendars")
    public List<ObjectNode> listCalendars() {
        return store.getCalendars().list();
    }

    @GetMapping("/calendars/count")
    public int countCalendars() {
        return store.getCalendars().count();
    }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ObjectNode> createCalendars(@RequestBody List<ObjectNode> calendars) {
        calendars.forEach(this::validateCalendar);
        if (store.getCalendars().count() + calendars.size() > props.limits().maximumCalendars()) {
            throw TalqApiException.unprocessable("announced maximumCalendars ("
                    + props.limits().maximumCalendars() + ") would be exceeded");
        }
        calendars.forEach(store.getCalendars()::insertNew);
        return calendars;
    }

    @PutMapping("/calendars")
    public List<ObjectNode> replaceCalendars(@RequestBody List<ObjectNode> calendars) {
        calendars.forEach(this::validateCalendar);
        return calendars.stream()
                .map(c -> store.getCalendars().replaceExisting(store.getCalendars().keyOf(c), c))
                .toList();
    }

    @GetMapping("/calendars/{calendarAddress}")
    public ObjectNode getCalendar(@PathVariable String calendarAddress) {
        return store.getCalendars().getOr404(calendarAddress);
    }

    @PutMapping("/calendars/{calendarAddress}")
    public ObjectNode replaceCalendar(@PathVariable String calendarAddress,
                                      @RequestBody ObjectNode calendar) {
        validateCalendar(calendar);
        return store.getCalendars().replaceExisting(calendarAddress, calendar);
    }

    @DeleteMapping("/calendars/{calendarAddress}")
    public ObjectNode deleteCalendar(@PathVariable String calendarAddress) {
        return store.getCalendars().delete(calendarAddress);
    }

    // ── /control-programs ───────────────────────────────────────────────

    @GetMapping("/control-programs")
    public List<ObjectNode> listPrograms() {
        return store.getControlPrograms().list();
    }

    @GetMapping("/control-programs/count")
    public int countPrograms() {
        return store.getControlPrograms().count();
    }

    @PostMapping("/control-programs")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ObjectNode> createPrograms(@RequestBody List<ObjectNode> programs) {
        programs.forEach(this::validateProgram);
        if (store.getControlPrograms().count() + programs.size() > props.limits().maximumPrograms()) {
            throw TalqApiException.unprocessable("announced maximumPrograms ("
                    + props.limits().maximumPrograms() + ") would be exceeded");
        }
        programs.forEach(store.getControlPrograms()::insertNew);
        return programs;
    }

    @PutMapping("/control-programs")
    public List<ObjectNode> replacePrograms(@RequestBody List<ObjectNode> programs) {
        programs.forEach(this::validateProgram);
        return programs.stream()
                .map(p -> store.getControlPrograms()
                        .replaceExisting(store.getControlPrograms().keyOf(p), p))
                .toList();
    }

    @GetMapping("/control-programs/{controlProgramAddress}")
    public ObjectNode getProgram(@PathVariable String controlProgramAddress) {
        return store.getControlPrograms().getOr404(controlProgramAddress);
    }

    @PutMapping("/control-programs/{controlProgramAddress}")
    public ObjectNode replaceProgram(@PathVariable String controlProgramAddress,
                                     @RequestBody ObjectNode program) {
        validateProgram(program);
        return store.getControlPrograms().replaceExisting(controlProgramAddress, program);
    }

    @DeleteMapping("/control-programs/{controlProgramAddress}")
    public ObjectNode deleteProgram(@PathVariable String controlProgramAddress) {
        return store.getControlPrograms().delete(controlProgramAddress);
    }

    // ── /assign-commands ────────────────────────────────────────────────

    @GetMapping("/assign-commands")
    public List<ObjectNode> listAssignments() {
        return store.getAssignCommands();
    }

    @PostMapping("/assign-commands")
    @ResponseStatus(HttpStatus.CREATED)
    public void assign(@RequestBody ObjectNode command) {
        var entity = command.path("entity");
        if (!command.has("addresses") || entity.isMissingNode() || entity.isNull()) {
            throw TalqApiException.badRequest("assign command requires 'addresses' and 'entity'");
        }
        requireReferenceExists(entity);
        for (JsonNode target : command.path("addresses")) {
            requireReferenceExists(target);
        }
        store.getAssignCommands().add(command);
    }

    // ── /override-commands ──────────────────────────────────────────────

    @PostMapping("/override-commands")
    @ResponseStatus(HttpStatus.CREATED)
    public void override(@RequestBody ObjectNode command) {
        for (JsonNode target : command.path("addresses")) {
            requireReferenceExists(target);
        }
        // TODO(ami-cim): translate targetCommand into a relay switch for the
        // referenced ZENIX devices.
    }

    private void validateCalendar(ObjectNode calendar) {
        if (!calendar.hasNonNull("id") || !calendar.hasNonNull("ownerCMS")) {
            throw TalqApiException.badRequest("calendar requires 'id' and 'ownerCMS'");
        }
        var rules = calendar.path("rules");
        if (rules.isArray() && rules.size() > props.limits().maxProgramsPerCalendar()) {
            throw TalqApiException.unprocessable("announced maxProgramsPerCalendar ("
                    + props.limits().maxProgramsPerCalendar() + ") would be exceeded");
        }
    }

    private void validateProgram(ObjectNode program) {
        if (!program.hasNonNull("id") || !program.hasNonNull("ownerCMS")
                || !program.hasNonNull("type")) {
            throw TalqApiException.badRequest(
                    "control program requires 'id', 'ownerCMS' and 'type'");
        }
    }

    /** Resolves a ResourceReference against the collections this gateway serves. */
    private void requireReferenceExists(JsonNode reference) {
        var resource = reference.path("resource").asText();
        var address = reference.path("address").asText();
        if (resource.isBlank() || address.isBlank()) {
            throw TalqApiException.badRequest(
                    "a resource reference requires 'resource' and 'address'");
        }
        TalqCollection collection = switch (resource) {
            case "devices" -> store.getDevices();
            case "groups" -> store.getGroups();
            case "calendars" -> store.getCalendars();
            case "control-programs" -> store.getControlPrograms();
            default -> null;
        };
        if (collection == null || collection.find(address).isEmpty()) {
            throw TalqApiException.relatedNotFound(
                    resource + " '" + address + "' does not exist on this gateway");
        }
    }
}
