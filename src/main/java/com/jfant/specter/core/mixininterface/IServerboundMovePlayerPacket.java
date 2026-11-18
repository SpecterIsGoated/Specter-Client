/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixininterface;

public interface IServerboundMovePlayerPacket {
    int meteor$getTag();

    void meteor$setTag(int tag);
}
