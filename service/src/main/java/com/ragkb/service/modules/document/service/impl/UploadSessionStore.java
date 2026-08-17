package com.ragkb.service.modules.document.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 内存上传会话仓库：管理 {@code init → parts → complete} 三阶段会话与分片落盘。
 *
 * <p>会话元数据存于 {@link ConcurrentHashMap}（单实例内存）；分片字节不驻内存，
 * 按 {@code {ragkb.storage.local-dir}/tmp/upload/{uploadId}/part-{n}} 落盘，
 * 避免大文件/多会话撑爆堆内存。会话 30 分钟无完成自动过期回收（后台守护线程）。
 *
 * <p>⚠️ 边界：本实现面向单实例开发/演示；多副本部署时会话需迁移到 Redis（分片元数据）
 * 与对象存储预签名直传（分片字节），见
 * {@code docs/modules/enterprise-generalization/design/document-upload-data-flow.md §3.2}。
 * 分片语义对齐 OpenAPI：{@code partSize=0} 表示直传（单分片=整个文件），否则分片上传、
 * 同 {@code partNumber} 重复 PUT 覆盖（幂等续传）。
 */
@Component
public class UploadSessionStore {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionStore.class);

    /** 会话存活时间：30 分钟未 complete 即回收。 */
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    /** 会话元数据（不可变字段 + 完成后回填的 {@code completedTaskId}）。 */
    public static final class Session {
        public final String uploadId;
        public final long tenantId;
        public final Long userId;
        public final long kbId;
        public final String fileName;
        public final long fileSize;
        public final String mimeType;
        public final String title;
        public final String sensitivity;
        /** 客户端预计算 sha256（仅用于秒传优化提示，完成时服务端重算为准）。 */
        public final String clientSha256;
        /** 分片大小（字节）；0 表示直传（单分片）。 */
        public final long partSize;
        public final int partCount;
        public final String idempotencyKey;
        public final Instant createdAt;
        /** complete 成功后的上传任务 id（幂等重放时直接返回）。 */
        public volatile String completedTaskId;

        public Session(String uploadId, long tenantId, Long userId, long kbId, String fileName, long fileSize,
                       String mimeType, String title, String sensitivity, String clientSha256,
                       long partSize, int partCount, String idempotencyKey, Instant createdAt) {
            this.uploadId = uploadId;
            this.tenantId = tenantId;
            this.userId = userId;
            this.kbId = kbId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.mimeType = mimeType;
            this.title = title;
            this.sensitivity = sensitivity;
            this.clientSha256 = clientSha256;
            this.partSize = partSize;
            this.partCount = partCount;
            this.idempotencyKey = idempotencyKey;
            this.createdAt = createdAt;
        }
    }

    /** 会话注册表：uploadId → 会话。 */
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /** 分片注册表：uploadId → 已接收分片号（有序集合，合并时按号升序拼接）。 */
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Integer>> uploadedParts = new ConcurrentHashMap<>();

    /** 幂等键 → uploadId（同文件重复 init 返回同一会话，实现秒传/幂等）。 */
    private final ConcurrentHashMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    /** 分片临时根目录：{localDir}/tmp/upload。 */
    private final Path tempRoot;

    public UploadSessionStore(@Value("${ragkb.storage.local-dir:./data/objects}") String localDir) {
        this.tempRoot = Path.of(localDir).toAbsolutePath().normalize().resolve("tmp/upload");
        // 守护线程周期性清理过期会话，避免长时间运行后会话表无限膨胀。
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "upload-session-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        cleaner.scheduleWithFixedDelay(this::expireStale, 10, 10, TimeUnit.MINUTES);
    }

    // ---------- 会话生命周期 ----------

    /** 创建会话并返回。uploadId 用 UUID 保证全局唯一。 */
    public Session create(Session session) {
        sessions.put(session.uploadId, session);
        uploadedParts.putIfAbsent(session.uploadId, new ConcurrentSkipListSet<>());
        if (session.idempotencyKey != null) {
            idempotencyIndex.putIfAbsent(session.idempotencyKey, session.uploadId);
        }
        return session;
    }

    /** 按 uploadId 查询会话；不存在或已过期返回空。 */
    public Optional<Session> get(String uploadId) {
        Session session = sessions.get(uploadId);
        if (session == null) {
            return Optional.empty();
        }
        // 超时会话视同不存在（惰性过期；后台线程还会物理回收）。
        if (session.createdAt.plus(SESSION_TTL).isBefore(Instant.now())) {
            sessions.remove(uploadId, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** 按幂等键查找未完成会话（同文件重复 init → 秒传/幂等）。 */
    public Optional<Session> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        String uploadId = idempotencyIndex.get(idempotencyKey);
        return uploadId == null ? Optional.empty() : get(uploadId);
    }

    /** 已上传分片号（有序，供断点续传提示与完整性校验）。 */
    public Set<Integer> uploadedParts(String uploadId) {
        return uploadedParts.getOrDefault(uploadId, new ConcurrentSkipListSet<>());
    }

    // ---------- 分片 ----------

    /**
     * 接收一个分片：校验分片号与字节数，写临时文件（同号覆盖=幂等）。
     *
     * @param session   当前会话
     * @param partNumber 分片号（1 起）
     * @param content   分片原始字节
     */
    public void putPart(Session session, int partNumber, byte[] content) {
        if (partNumber < 1 || partNumber > session.partCount) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "分片号越界：期望 1~" + session.partCount + "，实际 " + partNumber);
        }
        long expected = expectedPartSize(session, partNumber);
        if (content.length != expected) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "分片 " + partNumber + " 字节数不符：期望 " + expected + "，实际 " + content.length);
        }
        try {
            Path partFile = partFile(session.uploadId, partNumber);
            Files.createDirectories(partFile.getParent());
            Files.write(partFile, content);
            uploadedParts.computeIfAbsent(session.uploadId, key -> new ConcurrentSkipListSet<>()).add(partNumber);
        } catch (IOException e) {
            throw new UncheckedIOException("fail to store upload part: " + session.uploadId + "/" + partNumber, e);
        }
    }

    /** 会话是否已收齐全部所需分片（complete 前置校验）。 */
    public boolean isComplete(Session session) {
        return uploadedParts(session.uploadId).size() >= session.partCount;
    }

    /**
     * 按分片号升序合并全部已上传分片，返回完整文件字节（<b>不删除</b>分片与会话）。
     * complete 失败时可保留现场重试；成功由调用方显式 {@link #remove} 清理。
     */
    public Optional<byte[]> merge(Session session) {
        Set<Integer> parts = uploadedParts(session.uploadId);
        if (parts.size() < session.partCount) {
            return Optional.empty();
        }
        try (var buffer = new java.io.ByteArrayOutputStream()) {
            for (Integer number : parts) {
                buffer.write(Files.readAllBytes(partFile(session.uploadId, number)));
            }
            return Optional.of(buffer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("fail to merge upload parts: " + session.uploadId, e);
        }
    }

    /** 某分片应携带的字节数：直传=整个文件；多分片最后一片可为余量，其余固定分片大小。 */
    private long expectedPartSize(Session session, int partNumber) {
        if (session.partSize == 0) {
            return session.fileSize; // 直传：单分片即整个文件
        }
        if (partNumber < session.partCount) {
            return session.partSize;
        }
        long remainder = session.fileSize - (long) (session.partCount - 1) * session.partSize;
        return remainder > 0 ? remainder : session.partSize;
    }

    // ---------- 清理 ----------

    /** 移除会话及其分片临时目录（complete 成功后或初始化校验失败时调用）。 */
    public void remove(String uploadId) {
        Session session = sessions.remove(uploadId);
        uploadedParts.remove(uploadId);
        if (session != null && session.idempotencyKey != null) {
            idempotencyIndex.remove(session.idempotencyKey, uploadId);
        }
        Path dir = tempRoot.resolve(uploadId);
        try (Stream<Path> walk = Files.walk(dir)) {
            // 先删文件后删目录，避免目录非空删除失败。
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("fail to clean upload tmp: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("upload tmp dir not exists, skip cleanup: {}", dir);
        }
    }

    /** 后台任务：清理超过 TTL 的会话。 */
    private void expireStale() {
        Instant deadline = Instant.now().minus(SESSION_TTL);
        sessions.forEach((uploadId, session) -> {
            if (session.createdAt.isBefore(deadline)) {
                log.info("expire stale upload session: {}", uploadId);
                remove(uploadId);
            }
        });
    }

    /** 生成新的 uploadId。 */
    public static String newUploadId() {
        return UUID.randomUUID().toString();
    }

    /** 分片文件路径。 */
    private Path partFile(String uploadId, int partNumber) {
        return tempRoot.resolve(uploadId).resolve("part-" + partNumber);
    }
}
