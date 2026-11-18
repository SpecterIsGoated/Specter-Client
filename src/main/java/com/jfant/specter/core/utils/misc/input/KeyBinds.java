/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.utils.misc.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.jfant.specter.core.SpecterCore;
import net.minecraft.client.KeyMapping;

public class KeyBinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SpecterCore.identifier("specter"));

    public static KeyMapping OPEN_GUI = new KeyMapping("key.specter.open-gui", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY);
    public static KeyMapping OPEN_COMMANDS = new KeyMapping("key.specter.open-commands", InputConstants.Type.KEYBOARD, InputConstants.KEY_PERIOD, CATEGORY);

    private KeyBinds() {
    }

    public static KeyMapping[] apply(KeyMapping[] binds) {
        // Add key binding
        KeyMapping[] newBinds = new KeyMapping[binds.length + 2];

        System.arraycopy(binds, 0, newBinds, 0, binds.length);
        newBinds[binds.length] = OPEN_GUI;
        newBinds[binds.length + 1] = OPEN_COMMANDS;

        return newBinds;
    }
}
