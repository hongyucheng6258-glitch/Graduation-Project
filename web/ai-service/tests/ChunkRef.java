import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChunkRef {
    static final int OVERLAP = 80;

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            String[] fields = line.split("\t", 2);
            int size = Integer.parseInt(fields[0]);
            String text = new String(Base64.getDecoder().decode(fields[1]), StandardCharsets.UTF_8);
            List<String> parts = split(text, size);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append('\u0001');
                sb.append(Base64.getEncoder().encodeToString(parts.get(i).getBytes(StandardCharsets.UTF_8)));
            }
            out.println(sb);
        }
        out.flush();
    }

    static List<String> split(String raw, int chunkSize) {
        String text = raw.replaceAll("\\s+", " ").strip();
        List<String> parts = new ArrayList<>();
        if (text.isEmpty()) return parts;
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('。', end - 1), text.lastIndexOf(' ', end - 1));
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
                if (Character.isHighSurrogate(text.charAt(end - 1))) {
                    end--;
                }
            }
            parts.add(text.substring(start, end).strip());
            if (end >= text.length()) break;
            start = end - OVERLAP;
            if (Character.isLowSurrogate(text.charAt(start))) start++;
        }
        return parts;
    }
}
