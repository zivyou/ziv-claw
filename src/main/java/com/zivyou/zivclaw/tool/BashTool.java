package com.zivyou.zivclaw.tool;

import com.zivyou.zivclaw.context.Context;
import com.zivyou.zivclaw.registry.AbstractTool;
import com.zivyou.zivclaw.registry.AgentTool;
import com.zivyou.zivclaw.registry.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@AgentTool(
        name = "Bash",
        description = "运行bash命令"
)
public class BashTool extends AbstractTool<BashToolArg> {
    @Override
    public ToolResult invoke(Context context, BashToolArg args) {
        var workDir = context.getWorkDir();
        var cmd = args.getCmd();

        var processBuilder = new ProcessBuilder("bash", "-c", cmd);
        try {
            var process = processBuilder.start();
            var ret = process.waitFor(30, TimeUnit.SECONDS);
            if (!ret) {
                return ToolResult.error("命令执行超时！");
            }
            return ToolResult.ok("bash命令执行成功");
        } catch (InterruptedException e) {
            log.error("命令执行失败： {},  InterruptedException: {}", cmd, e.getMessage());
            return ToolResult.error("命令执行失败， InterruptedException");
        } catch (IOException e) {
            log.error("命令执行失败： {}, IOException: {}", cmd, e.getMessage());
            return ToolResult.error("命令执行失败， IOException");
        }
    }
}
