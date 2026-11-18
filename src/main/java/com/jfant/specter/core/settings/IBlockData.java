package com.jfant.specter.core.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.utils.misc.IChangeable;
import com.jfant.specter.core.utils.misc.ICopyable;
import com.jfant.specter.core.utils.misc.ISerializable;
import net.minecraft.world.level.block.Block;

public interface IBlockData<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> {
   WidgetScreen createScreen(GuiTheme var1, Block var2, BlockDataSetting<T> var3);
}
