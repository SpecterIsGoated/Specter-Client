/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.modintegration;


import java.util.function.BooleanSupplier;

/*
 * Hook for BadOptimizations mod compatibility.
 */
@SuppressWarnings("unused") // Used in fabric.mod.json
public class BadOptimizationsHook implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return false;
    }
}
