package com.cj.customquest.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 控制插件内置示例任务和示例对话只在首次安装时释放一次。
 */
public final class DefaultExamples {

    public static final String MARKER_NAME = ".examples-initialized";
    private static final String LEGACY_MARKER_NAME = ".defaults-initialized";

    private DefaultExamples() {
    }

    /**
     * 判断当前数据目录是否属于全新安装。
     * 已有内容但没有标记的目录视为旧版安装，避免升级时补回管理员删除的示例。
     */
    public static boolean shouldInstall(File dataFolder) {
        if (isInitialized(dataFolder)) {
            return false;
        }
        if (!dataFolder.exists()) {
            return true;
        }
        if (!dataFolder.isDirectory()) {
            return false;
        }
        File[] files = dataFolder.listFiles();
        return files != null && files.length == 0;
    }

    public static boolean isInitialized(File dataFolder) {
        return new File(dataFolder, MARKER_NAME).isFile()
                || new File(dataFolder, LEGACY_MARKER_NAME).isFile();
    }

    public static void markInitialized(File dataFolder) throws IOException {
        Files.createDirectories(dataFolder.toPath());
        File marker = new File(dataFolder, MARKER_NAME);
        if (!marker.exists()) {
            Files.createFile(marker.toPath());
        }
    }
}
