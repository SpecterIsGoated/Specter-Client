/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core;

import javax.swing.*;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Locale;

/**
 * Shown when the jar is double-clicked instead of being put in the mods folder. Opening the installer page or the
 * mods folder goes through {@link Desktop} (the operating system's own "open this"), not a spawned process.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        int option = JOptionPane.showOptionDialog(
            null,
            "To install Specter, put it in your mods folder and start Minecraft with Fabric.",
            "Specter",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            new String[]{"Open Fabric Installer", "Open Mods Folder"},
            null
        );

        if (!Desktop.isDesktopSupported()) return;
        Desktop desktop = Desktop.getDesktop();
        switch (option) {
            case 0:
                if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(new URI("https://fabricmc.net/use/installer/"));
                break;
            case 1: {
                File mods = new File(getModsFolder());
                if (!mods.exists()) mods.mkdirs();
                if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(mods);
                break;
            }
        }
    }

    private static String getModsFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return System.getenv("AppData") + "/.minecraft/mods";
        if (os.contains("mac")) return userHome + "/Library/Application Support/minecraft/mods";
        return userHome + "/.minecraft/mods";
    }
}
