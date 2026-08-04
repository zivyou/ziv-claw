package com.zivyou.zivclaw.session;

import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
public class Session {
    String id;
    String title;
    String workDir;
    List<Message> historyMessages;
    Date createTime;
    Date updateTime;

    public List<Message> getWorkingMemory() {
        return getWorkingMemory(6);
    }

    public List<Message> getWorkingMemory(int limit) {
        var messages = historyMessages;
        var last = messages.subList(Math.max(0, messages.size()-limit), messages.size());
        if (last.isEmpty()) return List.of();
        if (last.get(0).getRole().equals(Role.USER) && !last.get(0).getToolCallId().isBlank()) {
            last = last.subList(Math.max(0, last.size()-1), last.size());
        }
        return List.copyOf(last);
    }

    public Boolean appendSession(List<Message> messages) {
        updateTime = new Date();
        return historyMessages.addAll(messages);
    }

    public Boolean appendSession(Message message) {
        updateTime = new Date();
        return historyMessages.add(message);
    }
}
