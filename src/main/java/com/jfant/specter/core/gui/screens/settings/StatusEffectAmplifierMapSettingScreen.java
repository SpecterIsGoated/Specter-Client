/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.gui.screens.settings;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WIntEdit;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.utils.misc.Names;
import com.jfant.specter.core.utils.render.DisplayItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.apache.commons.lang3.Strings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class StatusEffectAmplifierMapSettingScreen extends WindowScreen {
    private final Setting<Reference2IntMap<MobEffect>> setting;

    private WTable table;

    private String filterText = "";

    public StatusEffectAmplifierMapSettingScreen(GuiTheme theme, Setting<Reference2IntMap<MobEffect>> setting) {
        super(theme, "Modify Amplifiers");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        List<MobEffect> statusEffects = new ArrayList<>(setting.get().keySet());
        statusEffects.sort(Comparator.comparing(Names::get));

        for (MobEffect statusEffect : statusEffects) {
            String name = Names.get(statusEffect);
            if (!Strings.CI.contains(name, filterText)) continue;

            table.add(theme.itemWithLabel(getPotionStack(statusEffect), name)).expandCellX();

            WIntEdit level = theme.intEdit(setting.get().getInt(statusEffect), 0, Integer.MAX_VALUE, true);
            level.action = () -> {
                setting.get().put(statusEffect, level.get());
                setting.onChanged();
            };

            table.add(level).minWidth(50);
            table.row();
        }
    }

    private ItemStack getPotionStack(MobEffect effect) {
        ItemStack potion = DisplayItemUtils.toStack(Items.POTION);

        potion.set(
            DataComponents.POTION_CONTENTS,
            new PotionContents(
                Optional.empty(),
                Optional.of(effect.getColor()),
                List.of(),
                Optional.empty()
            )
        );

        return potion;
    }
}
