package de.pumpecraft.enchants;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlotGroup;

public final class PumpeEnchantsBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
                for (CustomEnchant enchant : EnchantRegistry.definitions()) {
                    event.registry().register(
                        EnchantmentKeys.create(Key.key(enchant.key().toString())),
                        builder -> {
                            builder.description(Component.text(
                                    enchant.displayName(), enchant.rarity().color()))
                                .supportedItems(RegistrySet.keySet(
                                    RegistryKey.ITEM,
                                    enchant.allowedMaterials().stream()
                                        .map(material -> TypedKey.create(
                                            RegistryKey.ITEM,
                                            Key.key("minecraft", material.name()
                                                .toLowerCase(Locale.ROOT))))
                                        .toList()))
                                .weight(enchant.rarity().weight())
                                .maxLevel(enchant.maximumLevel())
                                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 10))
                                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(5, 10))
                                .anvilCost(enchant.rarity().anvilCost())
                                .activeSlots(EquipmentSlotGroup.ANY);
                            exclusiveWith(builder, enchant.incompatibleKeys());
                        });
                }
            }));
    }

    private void exclusiveWith(EnchantmentRegistryEntry.Builder builder, Set<NamespacedKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        builder.exclusiveWith(RegistrySet.keySet(
            RegistryKey.ENCHANTMENT,
            keys.stream()
                .map(key -> TypedKey.create(
                    RegistryKey.ENCHANTMENT, Key.key(key.toString())))
                .toList()));
    }
}
