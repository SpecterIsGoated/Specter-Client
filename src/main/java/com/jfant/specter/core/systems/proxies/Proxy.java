/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.systems.proxies;

import com.jfant.specter.core.settings.*;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.ISerializable;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

public class Proxy implements ISerializable<Proxy> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOptional = settings.createGroup("Optional");

    public Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the proxy.")
        .build()
    );

    public Setting<ProxyType> type = sgGeneral.add(new EnumSetting.Builder<ProxyType>()
        .name("type")
        .description("The type of proxy.")
        .defaultValue(ProxyType.Socks5)
        .build()
    );

    public Setting<String> address = sgGeneral.add(new StringSetting.Builder()
        .name("address")
        .description("The ip address of the proxy.")
        .filter(Utils::ipFilter)
        .build()
    );

    public Setting<Integer> port = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("The port of the proxy.")
        .defaultValue(0)
        .range(0, 65535)
        .sliderMax(65535)
        .noSlider()
        .build()
    );

    public Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Whether the proxy is enabled.")
        .defaultValue(true)
        .build()
    );

    // Optional

    public Setting<String> username = sgOptional.add(new StringSetting.Builder()
        .name("username")
        .description("The username of the proxy.")
        .build()
    );

    public Setting<String> password = sgOptional.add(new StringSetting.Builder()
        .name("password")
        .description("The password of the proxy.")
        .visible(() -> type.get().equals(ProxyType.Socks5))
        .build()
    );

    public Status status = Status.UNCHECKED;
    public long latency;

    // todo
    //  - add more rigorous status checks - perhaps querying our 'mcauth.meteorclient.com' or ccbluex's 'ping.liquidproxy.net'
    //  - the isSocks methods could be used to try and detect the version of added proxies when it's unclear -
    //     the only complication would be that some ips seem to be valid for both 4 and 5

    private Proxy() {
    }

    public Proxy(Tag tag) {
        fromTag((CompoundTag) tag);
    }

    public boolean resolveAddress() {
        return Utils.resolveAddress(this.address.get(), this.port.get());
    }

    public static class Builder {
        protected ProxyType type = ProxyType.Socks5;
        protected String address = "";
        protected int port = 0;
        protected String name = "";
        protected String username = "";
        protected String password = "";
        protected boolean enabled = false;

        public Builder type(ProxyType type) {
            this.type = type;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Proxy build() {
            Proxy proxy = new Proxy();

            if (!type.equals(proxy.type.getDefaultValue())) proxy.type.set(type);
            if (!address.equals(proxy.address.getDefaultValue())) proxy.address.set(address);
            if (port != proxy.port.getDefaultValue()) proxy.port.set(port);
            if (!name.equals(proxy.name.getDefaultValue())) proxy.name.set(name);
            if (!username.equals(proxy.username.getDefaultValue())) proxy.username.set(username);
            if (!password.equals(proxy.password.getDefaultValue())) proxy.password.set(password);
            if (enabled != proxy.enabled.getDefaultValue()) proxy.enabled.set(enabled);

            return proxy;
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public Proxy fromTag(CompoundTag tag) {
        tag.getCompound("settings").ifPresent(settings::fromTag);

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proxy proxy = (Proxy) o;
        return Objects.equals(proxy.address.get(), this.address.get()) && Objects.equals(proxy.port.get(), this.port.get());
    }

    public enum Status {
        UNCHECKED,
        CHECKING,
        DEAD,
        ALIVE;

        @Override
        public String toString() {
            return switch (this) {
                case UNCHECKED -> "";
                case CHECKING -> "...";
                case DEAD -> "X";
                case ALIVE -> "O";
            };
        }

        public Color getColor() {
            return switch (this) {
                case UNCHECKED, CHECKING -> Color.GRAY;
                case DEAD -> Color.RED;
                case ALIVE -> Color.GREEN;
            };
        }
    }
}
