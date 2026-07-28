package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;

@AgentTool(name = "Read", description = "read file content from file-system")
public class ReadTool implements Tool<String> {
    @Override
    public ToolResult invoke(Context context, String args) {
        return null;
    }

    @Override
    public Class<String> argsType() {
        return String.class;
    }
}
