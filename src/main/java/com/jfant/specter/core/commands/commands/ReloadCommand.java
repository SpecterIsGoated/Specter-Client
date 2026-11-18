/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.friends.Friend;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class ReloadCommand extends Command {
    public ReloadCommand() {
        super("reload", "Reloads many systems.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> {
            warning("Reloading systems, this may take a while.");

            Systems.load();
            Fonts.refresh();
            MeteorExecutor.execute(() -> Friends.get().forEach(Friend::updateInfo));

            return SINGLE_SUCCESS;
        });
    }
}
