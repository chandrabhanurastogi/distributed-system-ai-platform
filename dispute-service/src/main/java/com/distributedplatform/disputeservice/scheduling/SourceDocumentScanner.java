package com.distributedplatform.disputeservice.scheduling;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Component
public class SourceDocumentScanner {

    public List<Path> scan(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted()
                    .toList();
        }
    }
}
