package com.cj.customquest.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 页面交互接入必须保持"可选且无编译期依赖"：事件类与服务接口只能按名字反射解析，
 * 未安装 RPGCore 时整条链路跳过，原版箱子点击路径不受影响。
 *
 * <p>本测试同时钉住**页面契约**（页面 id、变量名、回包 id）：这些串任何一处改了名，
 * 推送都会静默落空 —— 页面照常显示，只是内容永远不更新，而且两端都不报错。
 */
class RpgCoreHookSourceTest {

    private static final String HOOK = "src/main/java/com/cj/customquest/hook/RpgCoreHook.java";

    @Test
    void resolvesThePageApiReflectivelyAndStaysOptional() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("Class.forName(name)"), "类必须按名字反射解析");
        assertTrue(hook.contains("com.rpgcore.gui.RpgCoreGuiPacketEvent"));
        assertTrue(hook.contains("com.rpgcore.gui.RpgCoreGuiHoverEvent"));
        assertTrue(hook.contains("com.rpgcore.gui.RpgCoreGui"), "服务接口要按名字解析");
        assertFalse(hook.contains("import com.rpgcore"), "不得引入 RPGCore 编译期依赖");
        assertTrue(hook.contains("registerEvent("), "必须通过事件总线注册，而不是 @EventHandler 编译期绑定");
        assertTrue(hook.contains("getServicesManager()"), "页面服务要从 ServicesManager 取");
    }

    @Test
    void wiresTheHookIntoThePluginAndTheDialogueModule() throws IOException {
        assertTrue(source("src/main/java/com/cj/customquest/CustomQuestPlugin.java")
                .contains("RpgCoreHook.register("));
        assertTrue(source("src/main/java/com/cj/customquest/listener/PlayerListener.java")
                .contains("RpgCoreHook.forget("), "退出事件里要真的调用清理");
        assertTrue(source("src/main/java/com/cj/customquest/navigation/NavigationManager.java")
                .contains("RpgCoreHook.showNavTip(player, tip)"), "导航开关先试页面提示，失败才回退动作栏");
    }

    /** 两个页面 id 与回包 id 必须与 gui/ 下的页面文件一一对应。 */
    @Test
    void keepsThePageContractAlignedWithTheShippedPages() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("BOARD_PAGE_ID = \"quest_board\""));
        assertTrue(hook.contains("DIALOGUE_PAGE_ID = \"npc_dialogue\""));
        assertTrue(hook.contains("customquest:board_nav"));
        assertTrue(hook.contains("customquest:board_scroll"));
        assertTrue(hook.contains("customquest:dialogue_select"));
        assertTrue(hook.contains("customquest:dialogue_skip"));
        assertTrue(hook.contains("BOARD_LINE_COUNT = 11"), "每屏行数必须与页面铺的 11 行一致");
    }

    /**
     * 正文由**服务端推进**逐字状态：新协议没有页面脚本、也没有页面→服务端回执，
     * 若不在这里排收尾任务，正文会永远停在打字态。
     */
    @Test
    void advancesTheRevealProgressOnTheServer() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("\"bodyText\""));
        assertTrue(hook.contains("\"typing\""));
        assertTrue(hook.contains("\"bodyReady\""));
        assertTrue(hook.contains("\"textReady\""));
        assertTrue(hook.contains("revealMillis("), "收尾时刻要按正文长度推算");
        assertTrue(hook.contains("scheduleRevealEnd("));
        assertTrue(hook.contains("finishReveal("));
        assertTrue(hook.contains("skipReveal("), "点\"跳过\"必须立刻收尾");
    }

    /**
     * 悬停高亮必须由服务端变量驱动：文本元素没有悬停贴图可切，只有客户端上报悬停、服务端置变量
     * 这条路。漏掉它，选项会永远不高亮，而且不报错。
     */
    @Test
    void drivesOptionHoverFromTheHoverEvent() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("handleHover("));
        assertTrue(hook.contains("\"option\" + option + \"Hover\""), "悬停变量名要与页面一致（option1Hover..）");
        assertTrue(hook.contains("getElementId"), "要读上报的元素 id");
    }

    /**
     * 面板内容与"位置/缩放公式要用到的变量"由本插件推；**位置本身不推**。
     *
     * <p>位置与整页缩放写在页面文件里、由客户端按实时窗口求值（见 {@link RpgCoreHookBoardOriginTest}），
     * 所以这里只剩内容与宽度这类事实：它们与窗口无关，推一次就够。
     */
    @Test
    void pushesTheBoardContentAndTheVariablesTheFormulasConsume() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("\"boardWidth\""), "页面公式要引用面板宽度");
        assertTrue(hook.contains("\"navColumnX\""), "导航列的 x 是页面坐标，由插件按宽度算");
        assertTrue(hook.contains("\"boardTitle\""), "面板标题");
        assertFalse(hook.contains("\"boardX\""), "位置由页面公式算，插件不该再推");
    }

    /** 行与导航按钮的纵向位置必须一起推，否则任务块下移时按钮会与行错位。 */
    @Test
    void shiftsTheLineAndItsNavigationButtonTogether() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("\"line\" + row + \"Y\""), "行要推绝对 y（lineNY）");
        assertTrue(hook.contains("\"nav\" + row + \"Y\""), "导航按钮要推同一个 y（navNY）");
        assertTrue(hook.contains("BOARD_BLOCK_GAP_PIXELS"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
