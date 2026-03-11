package com.simhospital.pathway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class PathwayLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new Jdk8Module())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Map<String, PathwayEventType> STEP_TYPE_MAP = Map.ofEntries(
            Map.entry("admission", PathwayEventType.ADMISSION),
            Map.entry("discharge", PathwayEventType.DISCHARGE),
            Map.entry("transfer", PathwayEventType.TRANSFER),
            Map.entry("order", PathwayEventType.ORDER),
            Map.entry("result", PathwayEventType.RESULT),
            Map.entry("delay", PathwayEventType.DELAY),
            Map.entry("registration", PathwayEventType.REGISTRATION),
            Map.entry("pre_admission", PathwayEventType.PRE_ADMISSION),
            Map.entry("add_person", PathwayEventType.ADD_PERSON),
            Map.entry("update_person", PathwayEventType.UPDATE_PERSON),
            Map.entry("merge", PathwayEventType.MERGE),
            Map.entry("bed_swap", PathwayEventType.BED_SWAP),
            Map.entry("transfer_in_error", PathwayEventType.TRANSFER_IN_ERROR),
            Map.entry("discharge_in_error", PathwayEventType.DISCHARGE_IN_ERROR),
            Map.entry("cancel_visit", PathwayEventType.CANCEL_VISIT),
            Map.entry("cancel_transfer", PathwayEventType.CANCEL_TRANSFER),
            Map.entry("cancel_discharge", PathwayEventType.CANCEL_DISCHARGE),
            Map.entry("pending_admission", PathwayEventType.PENDING_ADMISSION),
            Map.entry("pending_discharge", PathwayEventType.PENDING_DISCHARGE),
            Map.entry("pending_transfer", PathwayEventType.PENDING_TRANSFER),
            Map.entry("cancel_pending_admission", PathwayEventType.CANCEL_PENDING_ADMISSION),
            Map.entry("cancel_pending_discharge", PathwayEventType.CANCEL_PENDING_DISCHARGE),
            Map.entry("cancel_pending_transfer", PathwayEventType.CANCEL_PENDING_TRANSFER),
            Map.entry("delete_visit", PathwayEventType.DELETE_VISIT),
            Map.entry("track_departure", PathwayEventType.TRACK_DEPARTURE),
            Map.entry("track_arrival", PathwayEventType.TRACK_ARRIVAL),
            Map.entry("use_patient", PathwayEventType.USE_PATIENT),
            Map.entry("autogenerate", PathwayEventType.AUTO_GENERATE),
            Map.entry("clinical_note", PathwayEventType.CLINICAL_NOTE),
            Map.entry("hardcoded_message", PathwayEventType.HARDCODED_MESSAGE),
            Map.entry("document", PathwayEventType.DOCUMENT),
            Map.entry("generic", PathwayEventType.GENERIC),
            Map.entry("generate_resources", PathwayEventType.GENERATE_RESOURCES)
    );

    public Map<String, Pathway> load(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        Map<String, Pathway> result = new LinkedHashMap<>();
        loadAllFiles(directory, result);

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No recognisable pathway files found in " + directory);
        }
        return result;
    }

    private void loadAllFiles(Path directory, Map<String, Pathway> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{yml,yaml,json}")) {
            for (Path file : stream) {
                loadFile(file, result);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read pathway files from " + directory, e);
        }
    }

    private void loadFile(Path file, Map<String, Pathway> result) throws IOException {
        Map<String, RawPathway> rawPathways = MAPPER.readValue(
                file.toFile(),
                new TypeReference<Map<String, RawPathway>>() {}
        );
        for (Map.Entry<String, RawPathway> entry : rawPathways.entrySet()) {
            loadSinglePathway(entry.getKey(), entry.getValue(), result);
        }
    }

    private void loadSinglePathway(String name, RawPathway raw, Map<String, Pathway> result) {
        if (raw.pathway == null || raw.pathway.isEmpty()) return;

        List<PathwayEvent> events = raw.pathway.stream()
                .map(this::convertStep)
                .filter(Objects::nonNull)
                .toList();

        if (events.isEmpty()) return;
        result.put(name, new Pathway(name, events));
    }

    @SuppressWarnings("unchecked")
    private PathwayEvent convertStep(Map<String, Object> step) {
        Map<String, String> params = extractParameters(step);
        return findStepType(step, params);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractParameters(Map<String, Object> step) {
        Map<String, String> params = new LinkedHashMap<>();
        Object paramsObj = step.get("parameters");
        if (paramsObj instanceof Map<?, ?> paramsMap) {
            flattenMap("", (Map<String, Object>) paramsMap, params);
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private PathwayEvent findStepType(Map<String, Object> step, Map<String, String> params) {
        for (Map.Entry<String, Object> entry : step.entrySet()) {
            if ("parameters".equals(entry.getKey())) continue;

            PathwayEventType type = STEP_TYPE_MAP.get(entry.getKey());
            if (type == null) continue;

            mergeStepParams(entry.getValue(), params);
            Duration delay = (type == PathwayEventType.DELAY) ? parseDelay(params) : null;
            return new PathwayEvent(type, delay, Collections.unmodifiableMap(params));
        }
        return null;
    }

    private void mergeStepParams(Object value, Map<String, String> params) {
        if (!(value instanceof Map<?, ?> stepParams)) return;
        for (Map.Entry<?, ?> sp : stepParams.entrySet()) {
            params.put(String.valueOf(sp.getKey()),
                       sp.getValue() != null ? String.valueOf(sp.getValue()) : "");
        }
    }

    private Duration parseDelay(Map<String, String> params) {
        String from = params.get("from");
        String to = params.get("to");

        if (from == null) {
            throw new IllegalArgumentException("Delay step missing 'from' field");
        }
        if (to == null) {
            return parseDuration(from);
        }

        long midMillis = (parseDuration(from).toMillis() + parseDuration(to).toMillis()) / 2;
        return Duration.ofMillis(Math.max(midMillis, 1));
    }

    static Duration parseDuration(String s) {
        if (s == null || s.isEmpty()) return Duration.ZERO;

        long totalMillis = 0;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                num.append(c);
                continue;
            }
            if (num.isEmpty()) continue;

            double val = Double.parseDouble(num.toString());
            num.setLength(0);
            totalMillis += switch (c) {
                case 'h' -> (long) (val * 3_600_000);
                case 'm' -> parseMOrMs(s, i, val);
                case 's' -> (long) (val * 1_000);
                default -> (long) (val * 1_000);
            };
            if (c == 'm' && i + 1 < s.length() && s.charAt(i + 1) == 's') i++;
        }
        return Duration.ofMillis(totalMillis);
    }

    private static long parseMOrMs(String s, int pos, double val) {
        if (pos + 1 < s.length() && s.charAt(pos + 1) == 's') {
            return (long) val; // milliseconds
        }
        return (long) (val * 60_000); // minutes
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                flattenMap(key, (Map<String, Object>) value, out);
            } else {
                out.put(key, value != null ? String.valueOf(value) : "");
            }
        }
    }

    private static class RawPathway {
        @com.fasterxml.jackson.annotation.JsonProperty("percentage_of_patients")
        public Double percentageOfPatients;

        public Map<String, Object> persons;
        public Map<String, Object> consultant;
        public List<Map<String, Object>> pathway;

        @com.fasterxml.jackson.annotation.JsonProperty("historical_data")
        public List<Map<String, Object>> historicalData;
    }
}
