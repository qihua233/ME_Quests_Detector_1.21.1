package io.github.qihua233.ae2_ftbquest_detector.client;

import io.github.qihua233.ae2_ftbquest_detector.Config;
import io.github.qihua233.ae2_ftbquest_detector.TeamNameDisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

public final class DetectorConfigScreen {
    private DetectorConfigScreen() {
    }

    public static Screen create(Screen parent) {
        boolean serverConfigLoaded = Config.SERVER_SPEC.isLoaded();
        var singleplayerServer = Minecraft.getInstance().getSingleplayerServer();
        boolean hasLocalServerContext = singleplayerServer != null;
        boolean canEditServerConfig = singleplayerServer != null && !singleplayerServer.isPublished();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("ae2_ftbquest_detector.configuration.title"))
                .setDoesConfirmSave(true)
                .setSavingRunnable(() -> {
                    Config.saveConfigs(canEditServerConfig);
                    DetectorClientPreferencesSync.sendCurrent();
                });
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory client = builder.getOrCreateCategory(
                Component.translatable("ae2_ftbquest_detector.configuration.category.client"));
        ConfigCategory server = builder.getOrCreateCategory(
                Component.translatable("ae2_ftbquest_detector.configuration.category.server"));

        client.addEntry(entries.startBooleanToggle(
                        Component.translatable("ae2_ftbquest_detector.configuration.jadeShowOwnerInfo"),
                        Config.CLIENT_JADE_SHOW_OWNER_INFO.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ae2_ftbquest_detector.configuration.jadeShowOwnerInfo.tooltip"))
                .setSaveConsumer(Config.CLIENT_JADE_SHOW_OWNER_INFO::set)
                .build());
        client.addEntry(entries.startBooleanToggle(
                        Component.translatable("ae2_ftbquest_detector.configuration.jadeShowTaskProgress"),
                        Config.CLIENT_JADE_SHOW_TASK_PROGRESS.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ae2_ftbquest_detector.configuration.jadeShowTaskProgress.tooltip"))
                .setSaveConsumer(Config.CLIENT_JADE_SHOW_TASK_PROGRESS::set)
                .build());
        client.addEntry(entries.startEnumSelector(
                        Component.translatable("ae2_ftbquest_detector.configuration.teamNameDisplayMode"),
                        TeamNameDisplayMode.class,
                        Config.CLIENT_TEAM_NAME_DISPLAY_MODE.get())
                .setDefaultValue(TeamNameDisplayMode.NAME_AND_SHORT_ID)
                .setEnumNameProvider(mode -> Component.translatable(
                        "ae2_ftbquest_detector.configuration.teamNameDisplayMode." + mode.name()))
                .setTooltip(Component.translatable("ae2_ftbquest_detector.configuration.teamNameDisplayMode.tooltip"))
                .setSaveConsumer(Config.CLIENT_TEAM_NAME_DISPLAY_MODE::set)
                .build());

        if (serverConfigLoaded && hasLocalServerContext) {
            var ignoreHiddenEntry = entries.startBooleanToggle(
                            Component.translatable("ae2_ftbquest_detector.configuration.jadeTaskProgressIgnoreHiddenTasks"),
                            Config.SERVER_JADE_TASK_PROGRESS_IGNORE_HIDDEN_TASKS.get())
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable(
                            "ae2_ftbquest_detector.configuration.jadeTaskProgressIgnoreHiddenTasks.tooltip"))
                    .setSaveConsumer(Config.SERVER_JADE_TASK_PROGRESS_IGNORE_HIDDEN_TASKS::set)
                    .build();
            ignoreHiddenEntry.setEditable(canEditServerConfig);
            server.addEntry(ignoreHiddenEntry);

            var ignoreRepeatableEntry = entries.startBooleanToggle(
                            Component.translatable("ae2_ftbquest_detector.configuration.jadeTaskProgressIgnoreRepeatableTasks"),
                            Config.SERVER_JADE_TASK_PROGRESS_IGNORE_REPEATABLE_TASKS.get())
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable(
                            "ae2_ftbquest_detector.configuration.jadeTaskProgressIgnoreRepeatableTasks.tooltip"))
                    .setSaveConsumer(Config.SERVER_JADE_TASK_PROGRESS_IGNORE_REPEATABLE_TASKS::set)
                    .build();
            ignoreRepeatableEntry.setEditable(canEditServerConfig);
            server.addEntry(ignoreRepeatableEntry);
        } else if (serverConfigLoaded) {
            server.addEntry(entries.startTextDescription(Component.translatable(
                    "ae2_ftbquest_detector.configuration.serverManaged")).build());
        } else {
            server.addEntry(entries.startTextDescription(Component.translatable(
                    "ae2_ftbquest_detector.configuration.serverUnavailable")).build());
        }

        return builder.build();
    }
}
