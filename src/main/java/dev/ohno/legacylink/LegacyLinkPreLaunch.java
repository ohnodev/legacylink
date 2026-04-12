package dev.ohno.legacylink;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Registers MixinExtras injectors ({@code @WrapMethod}, etc.) before mixin application. */
public final class LegacyLinkPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        MixinExtrasBootstrap.init();
    }
}
