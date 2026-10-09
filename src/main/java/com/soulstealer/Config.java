package com.soulstealer;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BREAK_TREES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        BREAK_TREES = builder
                .comment("If true, fake players really break logs of natural trees when they 'mine wood'.",
                        "If false they only play the mining animation and the tree stays untouched.")
                .define("fakePlayersBreakTrees", true);
        SPEC = builder.build();
    }

    private Config() {
    }
}
