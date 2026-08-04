package com.zivyou.zivclaw.session;

public interface SessionManager {

    Session getOrCreateSession(String id, String workDir);

    Boolean save(String id);
}
