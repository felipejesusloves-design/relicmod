package com.example.relicmod;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ModItems {
    public static final List<RelicItem> RELICS = new ArrayList<>();

    public static final RelicItem IGNEOUS = relic("igneous_relic", Relics.Igneous::new);
    public static final RelicItem GLACIAL = relic("glacial_relic", Relics.Glacial::new);
    public static final RelicItem SHADOW  = relic("shadow_relic", Relics.Shadow::new);
    public static final RelicItem STORM   = relic("storm_relic", Relics.Storm::new);

    private static <T extends RelicItem> T relic(String name, Function<Item.Settings, T> factory) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(RelicMod.MOD_ID, name));
        T item = Registry.register(Registries.ITEM, key, factory.apply(new Item.Settings().registryKey(key)));
        RELICS.add(item);
        return item;
    }

    public static void init() {}
}
