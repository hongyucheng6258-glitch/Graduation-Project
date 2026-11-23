/*
 * 检索一致性评估（需在容器网络内运行）。
 *
 * 用法：
 *   1. 组装 6 页语料：把 docker/demo/knowledge 的 6 份 PDF 合并为 combined-guide.pdf；
 *   2. 复制本类、PDF 与 questions-50.txt 到 backend 容器；
 *   3. 用已构建的 app.jar 展开出的 classes/lib 作为 classpath 运行本类，需设置
 *      CAMPUS_AI_SERVICE_TOKEN，并在环境变量中存在 ai-service 与 qdrant。
 *
 * 本地实现写入独立集合 campus_eval_local，运行结束自动删除，不触碰生产集合。
 */
package com.campus.platform.module.ai.rag;


import com.campus.platform.module.ai.mapper.KnowledgeMapper.ChunkRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 检索一致性评估：同一份 PDF 分别用 Java 本地实现与 Python 服务建索引，
 * 对同一批问题比较 top-5 的页码集合、top-1 页码与 top-1 切片文本。
 *
 * 本地实现写入独立集合 campus_eval_local，评估结束即删除，不触碰生产集合。
 */
public class RagConsistencyEval {
    private static final long DOC_ID = 991100L;
    private static final int CHUNK = 400;
    private static final int TOP = 5;

    public static void main(String[] args) throws Exception {
        String token = System.getenv("CAMPUS_AI_SERVICE_TOKEN");
        var mapper = new ObjectMapper();
        var local = new KnowledgeVectorClient(mapper, "http://embedding:11434", "bge-small-zh-v1.5-onnx",
                "http://qdrant:6333", "campus_eval_local", 0.40, 400);
        var python = new PythonRagClient(mapper, "python", false, CHUNK, "http://ai-service:8000", token, 25000, 120000);

        byte[] content = Files.readAllBytes(Path.of("/tmp/combined-guide.pdf"));

        // 1) 本地实现建索引
        long start = System.nanoTime();
        var parts = new PdfChunker(CHUNK).split(content);
        List<String> localIds = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            localIds.add(UUID.randomUUID().toString());
        }
        var vectors = local.embed(parts.stream().map(PdfChunker.Part::text).toList());
        local.upsert(DOC_ID, 0, "ADMIN", "eval-local", localIds, vectors);
        long localIndexMs = (System.nanoTime() - start) / 1_000_000;
        Map<String, int[]> localMeta = new HashMap<>();
        Map<String, String> localText = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            localMeta.put(localIds.get(i), new int[] {parts.get(i).page()});
            localText.put(localIds.get(i), parts.get(i).text());
        }

        // 2) Python 建索引
        start = System.nanoTime();
        List<ChunkRow> rows = python.index(DOC_ID, 0, "ADMIN", "eval-py", content,
                System.nanoTime() + Duration.ofMinutes(2).toNanos());
        long pythonIndexMs = (System.nanoTime() - start) / 1_000_000;
        Map<String, int[]> pyMeta = new HashMap<>();
        Map<String, String> pyText = new HashMap<>();
        for (ChunkRow row : rows) {
            pyMeta.put(row.id(), new int[] {row.page()});
            pyText.put(row.id(), row.body());
        }

        System.out.printf("chunks local=%d python=%d | indexMs local=%d python=%d%n",
                parts.size(), rows.size(), localIndexMs, pythonIndexMs);

        // 3) 逐题比对
        List<String> lines = Files.readAllLines(Path.of("/tmp/questions.txt"), StandardCharsets.UTF_8);
        int top1Page = 0, top1Text = 0, bothTarget = 0, localTarget = 0, pyTarget = 0;
        double jaccardSum = 0;
        List<Long> localMs = new ArrayList<>(), pyMs = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int target = Integer.parseInt(line.substring(0, line.indexOf('\t')));
            String question = line.substring(line.indexOf('\t') + 1).trim();

            long t0 = System.nanoTime();
            List<String> localIdsHit = local.search(0L, DOC_ID, question,
                    System.nanoTime() + Duration.ofSeconds(25).toNanos(), true);
            localMs.add((System.nanoTime() - t0) / 1_000_000);

            t0 = System.nanoTime();
            List<String> pyIdsHit = python.search(0L, DOC_ID, question,
                    System.nanoTime() + Duration.ofSeconds(25).toNanos(), true);
            pyMs.add((System.nanoTime() - t0) / 1_000_000);

            List<String> l5 = head(localIdsHit, TOP);
            List<String> p5 = head(pyIdsHit, TOP);
            if (l5.isEmpty() || p5.isEmpty()) {
                mismatches.add(question + " -> 无召回");
                continue;
            }
            Set<Integer> lPages = pages(l5, localMeta);
            Set<Integer> pPages = pages(p5, pyMeta);
            int lTop = localMeta.get(l5.get(0))[0];
            int pTop = pyMeta.get(p5.get(0))[0];
            if (lTop == pTop) {
                top1Page++;
            }
            if (localText.get(l5.get(0)).strip().equals(pyText.get(p5.get(0)).strip())) {
                top1Text++;
            }
            jaccardSum += jaccard(lPages, pPages);
            if (lTop == target && pTop == target) {
                bothTarget++;
            }
            if (lTop == target) {
                localTarget++;
            }
            if (pTop == target) {
                pyTarget++;
            }
            if (lTop != pTop || jaccard(lPages, pPages) < 1.0) {
                mismatches.add(String.format("%s | local p%d %s vs python p%d %s", question, lTop, lPages, pTop, pPages));
            }
        }

        // 4) 清理
        local.delete(DOC_ID, "eval-local");
        python.delete(DOC_ID, "eval-py");

        int total = localMs.size();
        System.out.printf("questions=%d%n", total);
        System.out.printf("top1PageAgreement=%.1f%% top1TextAgreement=%.1f%% meanJaccard=%.3f%n",
                pct(top1Page, total), pct(top1Text, total), jaccardSum / total);
        System.out.printf("top1TargetHit local=%.1f%% python=%.1f%% both=%.1f%%%n",
                pct(localTarget, total), pct(pyTarget, total), pct(bothTarget, total));
        System.out.printf("latency local p50=%dms p95=%dms | python p50=%dms p95=%dms%n",
                percentile(localMs, 50), percentile(localMs, 95), percentile(pyMs, 50), percentile(pyMs, 95));
        System.out.println("mismatch=" + mismatches.size());
        mismatches.stream().limit(8).forEach(m -> System.out.println("  " + m));
    }

    private static List<String> head(List<String> ids, int size) {
        return ids.size() <= size ? ids : ids.subList(0, size);
    }

    private static Set<Integer> pages(List<String> ids, Map<String, int[]> meta) {
        Set<Integer> pages = new TreeSet<>();
        ids.forEach(id -> pages.add(meta.get(id)[0]));
        return pages;
    }

    private static double jaccard(Set<Integer> a, Set<Integer> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<Integer> union = new LinkedHashSet<>(a);
        union.addAll(b);
        Set<Integer> inter = new LinkedHashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / union.size();
    }

    private static double pct(int value, int total) {
        return total == 0 ? 0 : value * 100.0 / total;
    }

    private static long percentile(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
