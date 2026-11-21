/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.utils;

import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.tabs.Tabs;
import com.jfant.specter.core.pathing.PathManagers;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.renderer.FullScreenRenderer;
import com.jfant.specter.core.renderer.Renderer2D;
import com.jfant.specter.core.utils.misc.CPSUtils;
import com.jfant.specter.core.utils.misc.FakeClientPlayer;
import com.jfant.specter.core.utils.misc.MeteorStarscript;
import com.jfant.specter.core.utils.misc.Names;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.jfant.specter.core.utils.player.EChestMemory;
import com.jfant.specter.core.utils.player.Rotations;
import com.jfant.specter.core.utils.render.PlayerHeadUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.RainbowColors;
import com.jfant.specter.core.utils.world.BlockIterator;
import com.jfant.specter.core.utils.world.BlockUtils;

import java.lang.annotation.Annotation;

/**
 * Runs the {@link PreInit} / {@link PostInit} methods.
 * <p>
 * Meteor finds these by scanning the jar with the reflections library, which bundles javassist
 * (class generation, JVM attach, remote class loading). Malware scanners flag javassist, so Specter
 * calls the methods from a fixed list instead and ships neither library.
 * <p>
 * <b>Adding a new @PreInit / @PostInit method? Add it here too</b>, after anything it depends on.
 */
public class ReflectInit {
    private ReflectInit() {
    }

    /** Kept for API compatibility; nothing to register without classpath scanning. */
    public static void registerPackages() {
    }

    public static void init(Class<? extends Annotation> annotation) {
        if (annotation == PreInit.class) preInit();
        else if (annotation == PostInit.class) postInit();
    }

    private static void preInit() {
        MeteorExecutor.init();
        PathManagers.init();
        Renderer2D.init();
        FullScreenRenderer.init();
        Fonts.refresh();
        GuiThemes.init();
        Tabs.init();                // depends on PathManagers
        CPSUtils.init();
        MeteorStarscript.init();    // depends on PathManagers
        FakeClientPlayer.init();
        Names.init();
        EChestMemory.init();
        Rotations.init();
        Utils.init();
        BlockIterator.init();
        BlockUtils.init();
    }

    private static void postInit() {
        GuiThemes.postInit();
        GuiRenderer.init();
        ChatUtils.init();
        RainbowColors.init();
        PlayerHeadUtils.init();
        RenderUtils.init();
        Commands.init();            // depends on PathManagers
    }
}
