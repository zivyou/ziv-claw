package com.zivyou.zivclaw.session;
import com.zivyou.zivclaw.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.Null;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RequiredArgsConstructor
public class DefaultSessionManager implements SessionManager {
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public @Null Session getOrCreateSession(String id, String workDir) {
        if (null == id || id.isBlank()) {
            var timestamp = new Date();
            var sessionId = UUID.randomUUID().toString();
            var session = Session.builder().id(sessionId).workDir(workDir)
                    .historyMessages(new CopyOnWriteArrayList<>())
                    .createTime(timestamp).updateTime(timestamp).build();
            sessions.put(sessionId, session);
            return session;
        }

        if (!sessions.containsKey(id)) {
            return null;
        }

        return (sessions.get(id));
    }

    @Override
    public Boolean save(String id) {
        if (!sessions.containsKey(id)) return false;
        String filePath = String.format("./.session/%s", id);
        Path path = Paths.get(filePath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, JsonUtil.stringify(sessions.get(id)));
        } catch (IOException e) {
            log.error("save session to file failed!", e);
            throw new RuntimeException(e);
        }
        return true;
    }
}
