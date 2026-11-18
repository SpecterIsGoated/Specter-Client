/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.systems.modules;

import com.jfant.specter.SpecterClientAddon;


public class Categories {
    public static boolean REGISTERING;

    public static void init() {
        REGISTERING = true;
        SpecterClientAddon.registerCategories();
        REGISTERING = false;
    }
}
