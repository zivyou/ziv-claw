package com.zivyou.zivclaw.session;

public interface SessionManager {

    Session getOrCreateSession(String id, String workDir);

    Boolean save(String id);

    /** 清除某个会话的内存态与磁盘文件，返回是否确有其会话被清除。 */
    Boolean reset(String id);
}
