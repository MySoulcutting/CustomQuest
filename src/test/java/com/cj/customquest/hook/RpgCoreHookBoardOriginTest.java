package com.cj.customquest.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务面板的**职责边界**：位置与缩放不归本插件算。
 *
 * <p>此前这里是"面板原点 = 目标原点 − 静态原点"的算术，外加"等客户端上报窗口大小、最多重试 10 次"
 * 的调度。两者的共同毛病是**位置依赖客户端窗口，而窗口大小只有客户端知道**：进服首次推送必然早于
 * 客户端上报（位置先错一次），玩家改窗口后更是没有任何时机重算（面板停在旧位置，缩小窗口直接跑到
 * 屏幕外）。重试只是把同一个错误位置推迟 50 毫秒，治不了根。
 *
 * <p>现在位置与缩放写成页面文件里的公式（{@code quest_board.yml} 的 {@code x/y/scale-variable}，
 * 如 {@code window.width * 0.8 - boardWidth * page.scale}），由 RPGCore 在**客户端**按实时窗口求值 ——
 * 窗口一变位置立刻跟着变，也不多发一个包。本插件只负责推内容与 {@code boardWidth} 这类**变量**，
 * 不再算位置，也不再监听窗口大小事件。
 *
 * <p>公式本身的算术与失败回落由客户端侧的单测钉住（{@code GuiExpressionTest} / {@code GuiLayoutTest}）；
 * 这里钉住的是本插件这一侧的边界 —— 一旦有人又把位置算回插件里，这个测试会红。
 */
class RpgCoreHookBoardOriginTest {

    private static final String HOOK = "src/main/java/com/cj/customquest/hook/RpgCoreHook.java";

    /** 位置与缩放不再由本插件下发：这三个变量由页面公式代替。 */
    @Test
    void doesNotPushTheBoardPositionOrScaleAnyMore() throws IOException {
        String hook = source(HOOK);

        assertFalse(hook.contains("\"boardX\""), "面板 x 由页面公式按窗口算，插件不该再推");
        assertFalse(hook.contains("\"boardY\""), "面板 y 同上");
        assertFalse(hook.contains("\"boardScale\""), "整页缩放同上");
        assertFalse(hook.contains("boardOriginX") && hook.contains("boardOriginY"),
                "位置算术不该留在插件里");
    }

    /** 但仍然要推 {@code boardWidth}：页面公式用它把"面板右缘不出屏"算出来。 */
    @Test
    void stillPushesTheWidthTheFormulasConsume() throws IOException {
        String hook = source(HOOK);

        assertTrue(hook.contains("\"boardWidth\""), "页面公式要引用 boardWidth，必须继续推");
        assertTrue(hook.contains("\"navColumnX\""), "导航列的 x 是页面坐标，仍由插件按宽度算");
    }

    /** 不再监听窗口大小事件：没有要重算的东西，也就没有任何重试调度。 */
    @Test
    void noLongerReactsToWindowSizeChanges() throws IOException {
        String hook = source(HOOK);

        assertFalse(hook.contains("RpgCoreGuiViewEvent"), "位置由客户端公式求值，插件不必再接窗口事件");
        assertFalse(hook.contains("BOARD_ORIGIN_ATTEMPTS"), "重试轮询已删除");
        assertFalse(hook.contains("viewDimension"), "不再需要读客户端上报的窗口尺寸");
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
