package com.jfant.specter.core.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.utils.misc.ICopyable;
import com.jfant.specter.core.utils.misc.ISerializable;

public interface IGeneric<T extends IGeneric<T>> extends ICopyable<T>, ISerializable<T> {
   WidgetScreen createScreen(GuiTheme var1, GenericSetting<T> var2);
}
