package com.aiscientist.ai.wangwanying.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "jiebang.agent.experiment-design.repository", havingValue = "file")
public class FileEvidenceRepository implements EvidenceRepository {
    private final Path file;
    private final ObjectMapper mapper;
    private final Map<String, Evidence> records = new LinkedHashMap<>();

    public FileEvidenceRepository(
            @Value("${jiebang.agent.experiment-design.evidence-file}") String file,
            ObjectMapper mapper) {
        this.file = Path.of(file);
        this.mapper = mapper;
        load();
    }

    @Override
    public synchronized Evidence save(Evidence evidence) {
        records.put(evidence.id(), evidence);
        persist();
        return evidence;
    }

    @Override
    public synchronized List<Evidence> findAll() {
        return List.copyOf(records.values());
    }

    @Override
    public synchronized List<Evidence> search(String query, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Set<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return records.values().stream().limit(limit).toList();
        }
        return records.values().stream()
                .map(evidence -> Map.entry(evidence, score(evidence, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Evidence, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int score(Evidence evidence, Set<String> terms) {
        String text = evidence.searchableText();
        return terms.stream()
                .mapToInt(term -> text.contains(term) ? Math.max(1, term.length() / 2) : 0)
                .sum();
    }

    private Set<String> tokenize(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{N}]+"))
                .filter(value -> value.length() > 1)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<Evidence> evidence = mapper.readValue(file.toFile(), new TypeReference<>() { });
            evidence.forEach(item -> records.put(item.id(), item));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read evidence repository", error);
        }
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), records.values());
        } catch (IOException error) {
            throw new IllegalStateException("Failed to persist evidence repository", error);
        }
    }
}