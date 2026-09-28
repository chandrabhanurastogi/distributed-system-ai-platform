package com.distributedplatform.disputeservice.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceDocumentScannerTest {

    private final SourceDocumentScanner scanner = new SourceDocumentScanner();

    @Test
    void scan_withMixedFileTypes_returnsOnlyTxtFilesSortedByName(@TempDir Path tempDir) throws IOException {
        // Created deliberately out of alphabetical order, so a passing assertion on
        // sorted output actually proves sorting happened - not just that creation
        // order happened to already be alphabetical.
        Files.writeString(tempDir.resolve("charlie.txt"), "charlie");
        Files.writeString(tempDir.resolve("alpha.txt"), "alpha");
        Files.writeString(tempDir.resolve("bravo.txt"), "bravo");
        Files.writeString(tempDir.resolve("notes.md"), "not a policy document");
        // A directory with a ".txt"-looking name - proves the filter checks
        // isRegularFile, not just the filename suffix.
        Files.createDirectory(tempDir.resolve("delta.txt"));

        List<Path> result = scanner.scan(tempDir);

        List<String> fileNames = result.stream().map(path -> path.getFileName().toString()).toList();
        assertThat(fileNames).containsExactly("alpha.txt", "bravo.txt", "charlie.txt");
    }

    @Test
    void scan_whenPathIsNotADirectory_throwsIOException(@TempDir Path tempDir) throws IOException {
        Path notADirectory = tempDir.resolve("not-a-directory.txt");
        Files.writeString(notADirectory, "content");

        assertThatThrownBy(() -> scanner.scan(notADirectory))
                .isInstanceOf(IOException.class);
    }
}
