package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Paths;

@Slf4j
@AgentTool(name = "Read", description = "read file content from the file-system")
public class ReadTool extends AbstractTool<ReadToolArg> {
    @Override
    public ToolResult invoke(Context context, ReadToolArg args) {
        var basePath = Paths.get(context.getWorkDir());
        var filePath = Paths.get(args.getPath());
        var fullPath = basePath.resolve(filePath);
        try (var br = new BufferedReader(new FileReader(fullPath.toFile()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ( (line = br.readLine()) != null) sb.append(line);
            return ToolResult.ok(sb.toString());
        } catch (FileNotFoundException e) {
            log.error("file {} not found", fullPath);
            return ToolResult.error(String.format("file %s not exist", fullPath));
        } catch (IOException e) {
            log.error("io exception when read file: {}", fullPath, e);
            return ToolResult.error("io exception when read file");
        }
    }
}
