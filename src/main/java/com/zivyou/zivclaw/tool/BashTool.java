package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
@AgentTool(
        name = "Bash",
        description = "运行bash命令"
)
public class BashTool extends AbstractTool<BashToolArg> {
    @Override
    public ToolResult invoke(AgentContext agentContext, BashToolArg args) {
        var workDir = agentContext.getWorkDir();
        var cmd = args.getCmd();

        var processBuilder = new ProcessBuilder("bash", "-c", cmd);
        try {
            var process = processBuilder.start();
            String line; StringBuilder sb = new StringBuilder();
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            var ret = process.waitFor(30, TimeUnit.SECONDS);
            if (!ret) {
                return ToolResult.error("命令执行超时！");
            }
            return ToolResult.ok(sb.toString());
        } catch (InterruptedException e) {
            log.error("命令执行失败： {},  InterruptedException: {}", cmd, e.getMessage());
            return ToolResult.error("命令执行失败， InterruptedException");
        } catch (IOException e) {
            log.error("命令执行失败： {}, IOException: {}", cmd, e.getMessage());
            return ToolResult.error("命令执行失败， IOException");
        }
    }
}
