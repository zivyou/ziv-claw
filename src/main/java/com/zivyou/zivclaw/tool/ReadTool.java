package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.AgentContext;
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
    public ToolResult invoke(AgentContext agentContext, ReadToolArg args) {
        var basePath = Paths.get(agentContext.getWorkDir());
        var filePath = Paths.get(args.getPath());
        var fullPath = basePath.resolve(filePath);
        try (var br = new BufferedReader(new FileReader(fullPath.toFile()))) {
            int skip = args.getOffset() == null ? 0 : Math.max(args.getOffset(), 0);
            String line;
            while (skip > 0 && (line = br.readLine()) != null) skip--;

            long maxLines = args.getLimit() == null ? Long.MAX_VALUE : Math.max(args.getLimit(), 0);
            StringBuilder sb = new StringBuilder();
            long read = 0;
            while (read < maxLines && (line = br.readLine()) != null) {
                sb.append(line).append('\n');
                read++;
            }
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
