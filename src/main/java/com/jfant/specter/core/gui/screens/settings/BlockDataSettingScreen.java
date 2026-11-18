package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.screens.settings.base.CollectionMapSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.settings.BlockDataSetting;
import com.jfant.specter.core.settings.IBlockData;
import com.jfant.specter.core.utils.misc.IChangeable;
import com.jfant.specter.core.utils.misc.ICopyable;
import com.jfant.specter.core.utils.misc.ISerializable;
import com.jfant.specter.core.utils.misc.Names;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public class BlockDataSettingScreen<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> extends CollectionMapSettingScreen<Block, T> {
   private final BlockDataSetting<T> setting;
   private boolean invalidate;

   public BlockDataSettingScreen(GuiTheme theme, BlockDataSetting<T> setting) {
      super(theme, "Configure Blocks", setting, setting.get(), BuiltInRegistries.BLOCK);
      this.setting = setting;
   }

   protected boolean includeValue(Block value) {
      return value != Blocks.AIR;
   }

   protected WWidget getValueWidget(Block block) {
      return this.theme.itemWithLabel(block.asItem().getDefaultInstance(), Names.get(block));
   }

   protected WWidget getDataWidget(Block block, @Nullable T blockData) {
      WButton edit = this.theme.button(GuiRenderer.EDIT);
      edit.action = () -> {
         T data = blockData;
         if (blockData == null) {
            data = this.setting.defaultData.get().copy();
         }

         SpecterCore.mc.setScreen(data.createScreen(this.theme, block, this.setting));
         this.invalidate = true;
      };
      return edit;
   }

   @Override
   protected void onRenderBefore(GuiGraphics drawContext, float delta) {
      if (this.invalidate) {
         this.invalidateTable();
         this.invalidate = false;
      }
   }

   protected String[] getValueNames(Block block) {
      return new String[]{Names.get(block), BuiltInRegistries.BLOCK.getKey(block).toString()};
   }
}
