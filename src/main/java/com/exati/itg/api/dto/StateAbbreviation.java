package com.exati.itg.api.dto;

/**
 * Brazilian state (UF) codes accepted by the Exati Tier&nbsp;1
 * {@code state_abbreviation} field. Modeling it as an enum gives free
 * validation — an unknown UF is rejected as a 400 before the outbound call.
 */
public enum StateAbbreviation {
    AC, AL, AM, AP, BA, CE, DF, ES, GO, MA, MG, MS, MT, PA, PB, PE, PI,
    PR, RJ, RN, RO, RS, SC, SE, SP, TO
}
