package com.scheduler.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.model.TaskType;
import com.scheduler.service.SimulationConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Processes multiple CSV files in a single task.
 *
 * Payload format:
 *   {"files":["users.csv","products.csv"],"operation":"join|merge|compare","joinColumn":"id","outputFile":"result.csv"}
 *
 * Operations:
 *   merge   - concatenates rows from all files (must share same headers)
 *   compare - compares row counts and column overlap across files
 *   join    - inner join two files on a shared column
 */
@Component
public class MultiCsvTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MultiCsvTaskHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path OUTPUT_DIR = Paths.get("data", "output");

    private final SimulationConfigService simulationConfig;

    public MultiCsvTaskHandler(SimulationConfigService simulationConfig) {
        this.simulationConfig = simulationConfig;
    }

    @Override
    public TaskType getType() {
        return TaskType.MULTI_CSV_PROCESSING;
    }

    @Override
    public String execute(String taskId, String payload) throws Exception {
        log.info("[MULTI_CSV] Processing: {}", payload);

        if (ThreadLocalRandom.current().nextInt(100) < simulationConfig.getFailureRate(TaskType.MULTI_CSV_PROCESSING)) {
            throw new RuntimeException("Simulated failure during multi-CSV processing");
        }

        JsonNode json = mapper.readTree(payload);
        JsonNode filesNode = json.path("files");
        String operation = json.path("operation").asText("merge");

        if (!filesNode.isArray() || filesNode.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'files' array in payload");
        }

        List<String> fileNames = new ArrayList<>();
        for (JsonNode fn : filesNode) {
            fileNames.add(fn.asText());
        }

        if (fileNames.size() < 2) {
            throw new IllegalArgumentException("At least 2 files are required for multi-CSV processing");
        }

        // Validate and read all files
        List<CsvData> csvDataList = new ArrayList<>();
        for (String fileName : fileNames) {
            Path filePath = DATA_DIR.resolve(fileName).normalize();
            if (!filePath.startsWith(DATA_DIR)) {
                throw new SecurityException("Invalid file path: " + fileName);
            }
            if (!Files.exists(filePath)) {
                throw new FileNotFoundException("File not found: " + fileName);
            }
            List<String> lines = Files.readAllLines(filePath);
            if (lines.isEmpty()) {
                throw new RuntimeException("Empty CSV file: " + fileName);
            }
            String[] headers = lines.get(0).split(",");
            List<String[]> rows = lines.subList(1, lines.size()).stream()
                    .map(l -> l.split(","))
                    .collect(Collectors.toList());
            csvDataList.add(new CsvData(fileName, headers, rows));
            log.info("[MULTI_CSV] Read {} rows from {}", rows.size(), fileName);
        }

        switch (operation.toLowerCase()) {
            case "merge":
                return doMerge(taskId, csvDataList, json);
            case "compare":
                return doCompare(taskId, csvDataList);
            case "join":
                return doJoin(taskId, csvDataList, json);
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation + ". Supported: merge, compare, join");
        }
    }

    private String doMerge(String taskId, List<CsvData> csvDataList, JsonNode json) throws IOException {
        // Verify all files have same headers
        String[] baseHeaders = csvDataList.get(0).headers;
        for (int i = 1; i < csvDataList.size(); i++) {
            if (!Arrays.equals(baseHeaders, csvDataList.get(i).headers)) {
                throw new IllegalArgumentException("Cannot merge: headers differ between "
                        + csvDataList.get(0).fileName + " and " + csvDataList.get(i).fileName);
            }
        }

        List<String[]> allRows = new ArrayList<>();
        for (CsvData csv : csvDataList) {
            allRows.addAll(csv.rows);
        }

        String outfile = writeOutput(taskId, "merged", baseHeaders, allRows);
        return "Merged " + csvDataList.size() + " files -> " + allRows.size() + " total rows -> " + outfile;
    }

    private String doCompare(String taskId, List<CsvData> csvDataList) {
        StringBuilder sb = new StringBuilder("Comparison of " + csvDataList.size() + " files:\n");
        for (CsvData csv : csvDataList) {
            sb.append("  ").append(csv.fileName)
              .append(": ").append(csv.rows.size()).append(" rows, ")
              .append(csv.headers.length).append(" cols (")
              .append(String.join(", ", csv.headers)).append(")\n");
        }

        // Find common columns
        Set<String> common = new LinkedHashSet<>(Arrays.asList(csvDataList.get(0).headers));
        for (int i = 1; i < csvDataList.size(); i++) {
            common.retainAll(Arrays.asList(csvDataList.get(i).headers));
        }
        sb.append("Common columns: ").append(common.isEmpty() ? "none" : String.join(", ", common));

        return sb.toString();
    }

    private String doJoin(String taskId, List<CsvData> csvDataList, JsonNode json) throws IOException {
        if (csvDataList.size() != 2) {
            throw new IllegalArgumentException("Join operation requires exactly 2 files");
        }

        String joinColumn = json.path("joinColumn").asText("");
        if (joinColumn.isEmpty()) {
            throw new IllegalArgumentException("Missing 'joinColumn' field for join operation");
        }

        CsvData left = csvDataList.get(0);
        CsvData right = csvDataList.get(1);

        int leftIdx = findColumn(left.headers, joinColumn, left.fileName);
        int rightIdx = findColumn(right.headers, joinColumn, right.fileName);

        // Build index on right table
        Map<String, List<String[]>> rightIndex = new LinkedHashMap<>();
        for (String[] row : right.rows) {
            String key = row[rightIdx].trim().toLowerCase();
            rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // Build joined headers (left headers + right headers minus join column)
        List<String> joinedHeaders = new ArrayList<>(Arrays.asList(left.headers));
        for (int i = 0; i < right.headers.length; i++) {
            if (i != rightIdx) joinedHeaders.add(right.headers[i]);
        }

        // Inner join
        List<String[]> joinedRows = new ArrayList<>();
        for (String[] leftRow : left.rows) {
            String key = leftRow[leftIdx].trim().toLowerCase();
            List<String[]> matches = rightIndex.get(key);
            if (matches != null) {
                for (String[] rightRow : matches) {
                    List<String> combined = new ArrayList<>(Arrays.asList(leftRow));
                    for (int i = 0; i < rightRow.length; i++) {
                        if (i != rightIdx) combined.add(rightRow[i]);
                    }
                    joinedRows.add(combined.toArray(new String[0]));
                }
            }
        }

        String outFile = writeOutput(taskId, "joined", joinedHeaders.toArray(new String[0]), joinedRows);
        return "Joined " + left.fileName + " + " + right.fileName + " on '" + joinColumn
                + "': " + joinedRows.size() + " matched rows -> " + outFile;
    }

    private int findColumn(String[] headers, String column, String fileName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(column)) return i;
        }
        throw new IllegalArgumentException("Column '" + column + "' not found in " + fileName
                + ". Available: " + String.join(", ", headers));
    }

    private String writeOutput(String taskId, String suffix, String[] headers, List<String[]> rows) throws IOException {
        Path taskOutputDir = OUTPUT_DIR.resolve(taskId);
        Files.createDirectories(taskOutputDir);
        String outName = "multi_" + suffix + "_" + System.currentTimeMillis() + ".csv";
        Path outPath = taskOutputDir.resolve(outName);

        try (BufferedWriter writer = Files.newBufferedWriter(outPath)) {
            writer.write(String.join(",", headers));
            writer.newLine();
            for (String[] row : rows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }
        }

        log.info("[MULTI_CSV] Output written: {} ({} rows)", outPath, rows.size());
        return outPath.toString();
    }

    private static class CsvData {
        final String fileName;
        final String[] headers;
        final List<String[]> rows;

        CsvData(String fileName, String[] headers, List<String[]> rows) {
            this.fileName = fileName;
            this.headers = headers;
            this.rows = rows;
        }
    }
}
