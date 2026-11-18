/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.gui.themes.meteor.widgets.pressable;

import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.pressable.WFavorite;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorFavorite extends WFavorite implements MeteorWidget {
    public WMeteorFavorite(boolean checked) {
        super(checked);
    }

    @Override
    protected Color getColor() {
        return theme().favoriteColor.get();
    }
}
