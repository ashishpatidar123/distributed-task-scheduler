package com.scheduler.handler;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.model.TaskType;
import com.scheduler.service.SimulationConfigService;

/**
 * Real CSV processing handler.
 *
 * Payload format:
 *   {"input": "users.csv", "operation": "count|sort|filter|aggregate", "column": "age", "order": "asc|desc", "condition": ">30"}
 *
 * Operations:
 *   count     - returns row count
 *   sort      - sorts by column, writes sorted CSV to data/output/
 *   filter    - filters rows by condition (>N, <N, =value), writes to data/output/
 *   aggregate - computes min/max/avg/sum for a numeric column
 */
@Component
public class DataProcessingTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(DataProcessingTaskHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path OUTPUT_DIR = Paths.get("data", "output");

    private final SimulationConfigService simulationConfig;

    public DataProcessingTaskHandler(SimulationConfigService simulationConfig) {
        this.simulationConfig = simulationConfig;
    }

    @Override
    public TaskType getType() {
        return TaskType.DATA_PROCESSING;
    }

    @Override
    public String execute(String taskId, String payload) throws Exception {
        log.info("[DATA] Processing: {}", payload);

        // Simulate configurable failure rate
        if (ThreadLocalRandom.current().nextInt(100) < simulationConfig.getFailureRate(TaskType.DATA_PROCESSING)) {
            throw new RuntimeException("Out of memory while processing batch");
        }

        JsonNode json = mapper.readTree(payload);
        String inputFile = json.path("input").asText("");
        String operation = json.path("operation").asText("count");

        if (inputFile.isEmpty()) {
            throw new IllegalArgumentException("Missing 'input' field in payload");
        }

        // Security: prevent path traversal
        Path inputPath = DATA_DIR.resolve(inputFile).normalize();
        if (!inputPath.startsWith(DATA_DIR)) {
            throw new SecurityException("Invalid input path");
        }

        if (!Files.exists(inputPath)) {
            throw new FileNotFoundException("File not found: " + inputFile);
        }

        // Read CSV
        List<String> lines = Files.readAllLines(inputPath);
        if (lines.isEmpty()) {
            throw new RuntimeException("Empty CSV file: " + inputFile);
        }

        String[] headers = lines.get(0).split(",");
        List<String[]> rows = lines.subList(1, lines.size()).stream()
                .map(l -> l.split(","))
                .collect(Collectors.toList());

        log.info("[DATA] Read {} rows from {}", rows.size(), inputFile);

        switch (operation.toLowerCase()) {
            case "count":
                return "File: " + inputFile + " | Rows: " + rows.size() + " | Columns: " + headers.length + " (" + String.join(", ", headers) + ")";
            case "sort":
                return doSort(taskId, inputFile, headers, rows, json);
            case "filter":
                return doFilter(taskId, inputFile, headers, rows, json);
            case "aggregate":
                return doAggregate(taskId, inputFile, headers, rows, json);
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation + ". Supported: count, sort, filter, aggregate");
        }
    }

    private String doSort(String taskId, String inputFile, String[] headers, List<String[]> rows, JsonNode json) throws IOException {
        String column = json.path("column").asText("");
        String order = json.path("order").asText("asc");

        int colIdx = findColumn(headers, column);

        // Try numeric sort first, fall back to string sort
        boolean numeric = rows.stream().allMatch(r -> {
            try { Double.parseDouble(r[colIdx].trim()); return true; } catch (Exception e) { return false; }
        });

        rows.sort((a, b) -> {
            int cmp;
            if (numeric) {
                cmp = Double.compare(Double.parseDouble(a[colIdx].trim()), Double.parseDouble(b[colIdx].trim()));
            } else {
                cmp = a[colIdx].trim().compareToIgnoreCase(b[colIdx].trim());
            }
            return "desc".equalsIgnoreCase(order) ? -cmp : cmp;
        });

        String outFile = writeOutput(taskId, inputFile, "sorted", headers, rows);
        return "Sorted " + rows.size() + " rows by " + column + " " + order.toUpperCase() + " -> " + outFile;
    }

    private String doFilter(String taskId, String inputFile, String[] headers, List<String[]> rows, JsonNode json) throws IOException {
        String column = json.path("column").asText("");
        String condition = json.path("condition").asText("");

        int colIdx = findColumn(headers, column);

        if (condition.isEmpty()) {
            throw new IllegalArgumentException("Missing 'condition' field. Examples: >30, <100, =New York");
        }

        List<String[]> filtered;
        char op = condition.charAt(0);
        String value = condition.substring(1).trim();

        if (op == '>' || op == '<') {
            double threshold = Double.parseDouble(value);
            filtered = rows.stream().filter(r -> {
                try {
                    double v = Double.parseDouble(r[colIdx].trim());
                    return op == '>' ? v > threshold : v < threshold;
                } catch (Exception e) { return false; }
            }).collect(Collectors.toList());
        } else if (op == '=') {
            filtered = rows.stream()
                .filter(r -> r[colIdx].trim().equalsIgnoreCase(value))
                .collect(Collectors.toList());
        } else {
            // Treat entire condition as equality match
            filtered = rows.stream()
                .filter(r -> r[colIdx].trim().equalsIgnoreCase(condition))
                .collect(Collectors.toList());
        }

        String outFile = writeOutput(taskId, inputFile, "filtered", headers, filtered);
        return "Filtered " + column + " " + condition + ": " + filtered.size() + "/" + rows.size() + " rows matched -> " + outFile;
    }

    private String doAggregate(String taskId, String inputFile, String[] headers, List<String[]> rows, JsonNode json) {
        String column = json.path("column").asText("");
        int colIdx = findColumn(headers, column);

        List<Double> values = rows.stream()
                .map(r -> {
                    try { return Double.parseDouble(r[colIdx].trim()); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (values.isEmpty()) {
            throw new RuntimeException("No numeric values found in column: " + column);
        }

        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avg = sum / values.size();

        return String.format("Aggregate on %s.%s: count=%d, min=%.2f, max=%.2f, avg=%.2f, sum=%.2f",
                inputFile, column, values.size(), min, max, avg, sum);
    }

    private int findColumn(String[] headers, String column) {
        if (column.isEmpty()) {
            throw new IllegalArgumentException("Missing 'column' field in payload");
        }
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(column)) return i;
        }
        throw new IllegalArgumentException("Column '" + column + "' not found. Available: " + String.join(", ", headers));
    }

    private String writeOutput(String taskId, String inputFile, String suffix, String[] headers, List<String[]> rows) throws IOException {
        Path taskOutputDir = OUTPUT_DIR.resolve(taskId);
        Files.createDirectories(taskOutputDir);
        
        String baseName = inputFile.replace(".csv", "");
        String outName = baseName + "_" + suffix + "_" + System.currentTimeMillis() + ".csv";
        Path outPath = taskOutputDir.resolve(outName);

        try (BufferedWriter writer = Files.newBufferedWriter(outPath)) {
            writer.write(String.join(",", headers));
            writer.newLine();
            for (String[] row : rows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }
        }

        log.info("[DATA] Output written: {} ({} rows)", outPath, rows.size());
        return outPath.toString();
    }
}