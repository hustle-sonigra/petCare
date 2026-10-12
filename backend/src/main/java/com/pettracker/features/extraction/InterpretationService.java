package com.pettracker.features.extraction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

@Service
public class InterpretationService {
    private static final String DATE = "(?<!\\d)(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})(?!\\d)";
    private static final Pattern VACCINE = Pattern.compile("\\b(Rabies|DHPP|DHPPI|FVRCP|Bordetella|Leptospirosis|Parvovirus)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLINIC_LABEL = Pattern.compile("^(?:clinic(?: name)?|hospital(?: name)?|provider|veterinary clinic)\\s*[:：-]\\s*(.{1,120})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLINIC_LINE = Pattern.compile("^[\\p{L}\\p{N} &'’.,()-]{2,110}\\b(?:Clinic|Vets|Animal Hospital|Pet Care)(?:[ .,&'’()-]{0,10})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_LABEL = Pattern.compile("\\b(?:next\\s+(?:due(?:\\s+date)?|vaccination(?:\\s+date)?|booster(?:\\s+date)?)|due(?:\\s+date)?|booster(?:\\s+due|\\s+date)?)\\s*[:：-]?\\s*" + DATE, Pattern.CASE_INSENSITIVE);
    private static final Pattern ADMIN_LABEL = Pattern.compile("\\b(?:administered(?:\\s+(?:on|date))?|given(?:\\s+on)?|vaccination\\s+date|date\\s+(?:administered|given|of\\s+vaccination))\\s*[:：-]?\\s*" + DATE, Pattern.CASE_INSENSITIVE);

    public VaccinationExtractionDto interpret(String rawText) {
        Map<String, String> warnings = new LinkedHashMap<>();
        Map<String, String> evidence = new LinkedHashMap<>();
        Set<String> clinics = new LinkedHashSet<>(), vaccines = new LinkedHashSet<>();
        Set<LocalDate> administered = new LinkedHashSet<>(), due = new LinkedHashSet<>();
        for (String original : Objects.requireNonNullElse(rawText, "").split("\\R")) {
            String line = original.strip().replaceAll("[\\t ]+", " ");
            Matcher clinic = CLINIC_LABEL.matcher(line);
            if (clinic.matches()) clinics.add(clinic.group(1).strip());
            else if (CLINIC_LINE.matcher(line).matches()) clinics.add(line);
            Matcher vaccine = VACCINE.matcher(line);
            // Ignore explicit reminder rows when looking for the administered vaccine.
            if (!line.matches("(?i)^.*\\b(?:next|due|reminder)\\b.*$")) {
                while (vaccine.find()) {
                    vaccines.add(vaccine.group(1).toUpperCase(Locale.ROOT));
                    evidence.putIfAbsent("vaccineName", line);
                }
            }
            scanDates(line, DUE_LABEL, due, "nextDueOn", warnings, evidence);
            // Remove due-labelled spans so 'next vaccination date' is not also an administration date.
            scanDates(DUE_LABEL.matcher(line).replaceAll(""), ADMIN_LABEL, administered, "administeredOn", warnings, evidence);
        }
        String clinic = single(clinics, "clinicName", warnings);
        if (clinic != null) evidence.put("clinicName", clinic);
        String vaccine = single(vaccines, "vaccineName", warnings);
        LocalDate admin = single(administered, "administeredOn", warnings);
        LocalDate next = single(due, "nextDueOn", warnings);
        if (next != null && admin != null && !next.isAfter(admin)) {
            next = null;
            warnings.put("nextDueOn", "The detected due date is not after administration. Check the certificate.");
        }
        // An ambiguous occurrence makes the whole field uncertain, even if another date parsed.
        if (warnings.containsKey("administeredOn")) admin = null;
        if (warnings.containsKey("nextDueOn")) next = null;
        for (String field : List.of("clinicName", "vaccineName", "administeredOn", "nextDueOn"))
            warnings.putIfAbsent(field, evidence.containsKey(field)
                    ? "Automatically detected. Verify against the certificate."
                    : "No clearly labelled value found. Enter it manually or leave optional fields empty.");
        return new VaccinationExtractionDto(clinic, vaccine, admin, next, warnings, evidence);
    }

    private void scanDates(String line, Pattern pattern, Set<LocalDate> values, String field,
                           Map<String, String> warnings, Map<String, String> evidence) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String date = matcher.group(1).replace('/', '-');
            Set<LocalDate> possibilities = new LinkedHashSet<>();
            List<String> formats = date.matches("^\\d{4}-.*")
                    ? List.of("uuuu-M-d") : List.of("d-M-uuuu", "M-d-uuuu");
            for (String format : formats) {
                try { possibilities.add(LocalDate.parse(date, DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT))); }
                catch (DateTimeParseException ignored) { }
            }
            evidence.putIfAbsent(field, matcher.group());
            if (possibilities.size() == 1) values.add(possibilities.iterator().next());
            else warnings.put(field, possibilities.isEmpty()
                    ? "The labelled date is invalid. Enter the correct date."
                    : "Ambiguous day/month order. Check and enter the date manually.");
        }
    }

    private <T> T single(Set<T> values, String field, Map<String, String> warnings) {
        if (values.size() > 1) {
            warnings.put(field, "Multiple values found. Select the value for this vaccination manually.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }
}
