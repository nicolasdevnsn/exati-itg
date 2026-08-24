package com.exati.itg.talqserver;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of the TALQ error array this gateway returns to the CMS.
 * Mirrors {@code TALQErrorMessage} of the official data model: every error
 * response body is a JSON array of these, never a bare object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TalqError(String key, String description) {

    public static final String PAYLOAD_ERROR = "payloadError";
    public static final String RESOURCE_NOT_FOUND = "resourceNotFound";
    // Keep distinct from RESOURCE_NOT_FOUND: certifier tests GW_BV_005/006
    // assert this key for unknown class/function; only the undeclared
    // ATTRIBUTE case (GW_BV_008) asserts plain resourceNotFound.
    public static final String RELATED_RESOURCE_NOT_FOUND = "relatedResourceNotFound";
    public static final String RESOURCE_CONFLICT = "resourceConflict";
    public static final String METHOD_NOT_ALLOWED = "methodNotAllowed";
    public static final String PARAMETER_MISSING = "parameterMissing";
    public static final String PARAMETER_VALUE_NOT_VALID = "parameterValueNotValid";

    public static TalqError payload(String description) {
        return new TalqError(PAYLOAD_ERROR, description);
    }

    public static TalqError notFound(String description) {
        return new TalqError(RESOURCE_NOT_FOUND, description);
    }

    public static TalqError relatedNotFound(String description) {
        return new TalqError(RELATED_RESOURCE_NOT_FOUND, description);
    }

    public static TalqError conflict(String description) {
        return new TalqError(RESOURCE_CONFLICT, description);
    }

    public static TalqError parameterMissing(String description) {
        return new TalqError(PARAMETER_MISSING, description);
    }

    public static TalqError parameterValueNotValid(String description) {
        return new TalqError(PARAMETER_VALUE_NOT_VALID, description);
    }
}
