package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

@AgentTool(name = "Write", description = "创建文件，或者重写一个文件")
public class WriteTool extends AbstractTool<WriteToolArg> {
    @Override
    public ToolResult invoke(Context context, WriteToolArg args) {
        var basePath = Paths.get(context.getWorkDir());
        var relativePath = Paths.get(args.getPath());
        var filePath = basePath.resolve(relativePath);

        try (var file = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            file.write(args.getContent());
            return ToolResult.ok("write file succeeded");
        } catch (IOException e) {
            return ToolResult.error(String.format("write file failed: %s", e.getMessage()));
        }
    }
}
