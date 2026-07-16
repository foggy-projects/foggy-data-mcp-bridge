/*
 * Versioned, build-only reader for JaCoCo execution data. The Step 4 Python
 * verifier compiles this source against the frozen org.jacoco.core 0.8.12 JAR
 * and consumes its deterministic, URL-safe TSV output.
 */

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.internal.data.CRC64;

public final class JaCoCoExecInspector {

    private JaCoCoExecInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--class-tree".equals(args[0])) {
            inspectClassTree(Path.of(args[1]));
            return;
        }
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: JaCoCoExecInspector <absolute-exec-file> | --class-tree <absolute-directory>");
        }
        Path input = Path.of(args[0]);
        if (!input.isAbsolute() || !Files.isRegularFile(input) || Files.isSymbolicLink(input)) {
            throw new IllegalArgumentException("exec input must be an absolute regular file");
        }

        SessionInfoStore sessions = new SessionInfoStore();
        ExecutionDataStore executionData = new ExecutionDataStore();
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(input.toFile()))) {
            ExecutionDataReader reader = new ExecutionDataReader(stream);
            reader.setSessionInfoVisitor(sessions);
            reader.setExecutionDataVisitor(executionData);
            while (reader.read()) {
                // Read every block so truncated or corrupt trailing data is rejected.
            }
        }

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        List<SessionInfo> orderedSessions = new ArrayList<>(sessions.getInfos());
        orderedSessions.sort(Comparator
                .comparing(SessionInfo::getId)
                .thenComparingLong(SessionInfo::getStartTimeStamp)
                .thenComparingLong(SessionInfo::getDumpTimeStamp));
        for (SessionInfo session : orderedSessions) {
            System.out.printf("S\t%s\t%d\t%d%n",
                    encode(encoder, session.getId()),
                    session.getStartTimeStamp(),
                    session.getDumpTimeStamp());
        }

        List<ExecutionData> orderedClasses = new ArrayList<>(executionData.getContents());
        orderedClasses.sort(Comparator
                .comparing(ExecutionData::getName)
                .thenComparingLong(ExecutionData::getId));
        for (ExecutionData data : orderedClasses) {
            boolean[] probes = data.getProbes();
            int covered = 0;
            byte[] packedProbes = new byte[(probes.length + 7) / 8];
            for (int index = 0; index < probes.length; index++) {
                if (probes[index]) {
                    covered++;
                    packedProbes[index / 8] |= (byte) (1 << (index % 8));
                }
            }
            System.out.printf("C\t%016x\t%s\t%d\t%d\t%s%n",
                    data.getId(), encode(encoder, data.getName()), probes.length, covered,
                    encoder.encodeToString(packedProbes));
        }
    }

    private static void inspectClassTree(Path root) throws Exception {
        if (!root.isAbsolute() || !Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("class tree must be an absolute real directory");
        }
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        List<Path> classes;
        try (Stream<Path> paths = Files.walk(root)) {
            classes = paths.filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
        }
        for (Path path : classes) {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("class tree contains a non-regular class file");
            }
            String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
            String name = relative.substring(0, relative.length() - ".class".length());
            System.out.printf("F\t%016x\t%s%n", CRC64.classId(Files.readAllBytes(path)), encode(encoder, name));
        }
    }

    private static String encode(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
