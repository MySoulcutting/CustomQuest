package com.cj.customquest.listener;

import com.cj.customquest.quest.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.jar.JarFile;

/**
 * EpicCraftingsPlus 成功合成监听。
 * <p>
 * 官方 API 说明成功事件提供 getPlayer() 与 getCrafting()；这里使用运行时反射注册，
 * 避免将付费插件 JAR 提交进仓库，同时兼容其包路径变化。
 */
public final class EpicCraftingsPlusListener implements Listener {

    private static final List<String> EVENT_CLASSES = List.of(
            "ecp.ayunami2000.ayunEpicCraftings.events.EpicCraftingsCraftEvent",
            "ecp.ayunami2000.ayunEpicCraftingsPlus.events.EpicCraftingsCraftEvent",
            "com.epiccraftingsplus.events.EpicCraftingsCraftEvent",
            "com.gmail.ajneb97.events.EpicCraftingsCraftEvent"
    );

    private EpicCraftingsPlusListener() {
    }

    public static boolean register(Plugin owner) {
        Class<? extends Event> eventClass = findEventClass(owner);
        if (eventClass == null) {
            Bukkit.getLogger().warning("[CustomQuest] 已检测到 EpicCraftingsPlus，但未找到 EpicCraftingsCraftEvent；"
                    + "请确认插件版本提供官方 API 事件。");
            return false;
        }
        EventExecutor executor = (listener, event) -> handle(event);
        Bukkit.getPluginManager().registerEvent(
                eventClass, new EpicCraftingsPlusListener(), EventPriority.MONITOR, executor, owner, true);
        Bukkit.getLogger().info("[CustomQuest] 已注册 EpicCraftingsPlus 合成事件: " + eventClass.getName());
        return true;
    }

    private static Class<? extends Event> findEventClass(Plugin owner) {
        Plugin epic = Bukkit.getPluginManager().getPlugin("EpicCraftingsPlus");
        for (String className : EVENT_CLASSES) {
            Class<?> loaded = loadClass(className, epic, owner);
            if (loaded != null && Event.class.isAssignableFrom(loaded)) {
                return loaded.asSubclass(Event.class);
            }
        }
        Class<?> scanned = scanPluginJar(epic);
        if (scanned != null && Event.class.isAssignableFrom(scanned)) {
            return scanned.asSubclass(Event.class);
        }
        return null;
    }

    private static Class<?> scanPluginJar(Plugin epic) {
        if (epic == null || epic.getClass().getProtectionDomain().getCodeSource() == null) return null;
        URL location = epic.getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            File file = new File(location.toURI());
            if (!file.isFile()) return null;
            try (JarFile jar = new JarFile(file)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith("EpicCraftingsCraftEvent.class")) continue;
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    Class<?> loaded = loadClass(className, epic, epic);
                    if (loaded != null) return loaded;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Class<?> loadClass(String className, Plugin epic, Plugin owner) {
        for (ClassLoader loader : new ClassLoader[]{
                epic == null ? null : epic.getClass().getClassLoader(),
                owner.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        }) {
            if (loader == null) continue;
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static void handle(Event event) throws EventException {
        try {
            Object playerValue = invoke(event, "getPlayer");
            if (!(playerValue instanceof Player player)) return;
            Object crafting = invoke(event, "getCrafting");
            QuestManager.getInstance().onEpicCraft(player, resolveCraftingName(crafting));
        } catch (ReflectiveOperationException e) {
            throw new EventException(e);
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    /** 不同版本的 Crafting 模型可能将配置名称暴露为 getName/getId/getCraftingName。 */
    static String resolveCraftingName(Object crafting) {
        if (crafting == null) return null;
        for (String methodName : List.of("getName", "getId", "getCraftingName")) {
            try {
                Object value = invoke(crafting, methodName);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        String fallback = crafting.toString();
        return fallback == null || fallback.isBlank() ? null : fallback;
    }
}
