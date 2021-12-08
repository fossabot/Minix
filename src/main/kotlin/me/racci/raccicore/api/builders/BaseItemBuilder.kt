package me.racci.raccicore.api.builders

import me.racci.raccicore.api.utils.minecraft.ItemNBT
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer

@Suppress("UNCHECKED_CAST")
abstract class BaseItemBuilder<T : BaseItemBuilder<T>> protected constructor(var itemStack: ItemStack) {

    var meta : ItemMeta = if (itemStack.hasItemMeta()) itemStack.itemMeta else Bukkit.getItemFactory().getItemMeta(itemStack.type)

    var name: Component?
        get() = meta.displayName()
        set(component) {meta.displayName(component)}

    var lore: Component?
        get() = throw UnsupportedOperationException()
        set(component) {meta.lore(listOf(component))}
    open fun lore(vararg component: Component) : T {
        meta.lore(component.asList())
        return this as T
    }

    open fun lore(unit: List<Component>.() -> List<Component>) {
        meta.lore(unit.invoke(meta.lore() ?: emptyList()))
    }

    var amount: Int
        get() = itemStack.amount
        set(amount) {itemStack.amount = amount}

    open fun enchant(vararg enchants: Pair<Enchantment, Int>) : T {
        enchants.forEach{meta.addEnchant(it.first, it.second, true)}
        return this as T
    }

    open fun disenchant(vararg enchants: Enchantment) : T {
        enchants.filter(meta::hasEnchant).forEach(meta::removeEnchant)
        return this as T
    }

    open fun addFlag(vararg flags: ItemFlag) : T {
        meta.addItemFlags(*flags)
        return this as T
    }

    open fun removeFlag(vararg flags: ItemFlag) : T {
        meta.removeItemFlags(*flags)
        return this as T
    }

    var unbreakable: Boolean
        get() = meta.isUnbreakable
        set(boolean) {meta.isUnbreakable = boolean}
    open fun unbreakable(unbreakable: Boolean = true) : T {
        meta.isUnbreakable = unbreakable
        return this as T
    }

    var glow: Boolean
        get() = throw UnsupportedOperationException()
        set(boolean) {glow(boolean)}
    open fun glow(boolean: Boolean = true) : T {
        if(!meta.hasEnchants()) {
            meta.addEnchant(Enchantment.DURABILITY, 1, false)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        return this as T
    }

    var model: Int?
        get() = meta.customModelData
        set(modelData) = meta.setCustomModelData(modelData)

    val pdc get() = meta.persistentDataContainer
    open fun pdc(unit: PersistentDataContainer.() -> Unit) : T {
        unit.invoke(meta.persistentDataContainer)
        return this as T
    }

    open fun stringNBT(vararg pair: Pair<String, String>) : T {
        itemStack.itemMeta = meta
        pair.forEach{ItemNBT.setString(itemStack, it.first, it.second)}
        meta = itemStack.itemMeta
        return this as T
    }

    var nbt: Pair<String, Boolean>
        get() = throw UnsupportedOperationException()
        set(pair) {booleanNBT(pair)}
    open fun booleanNBT(vararg pair: Pair<String, Boolean>) : T {
        itemStack.itemMeta = meta
        pair.forEach{ItemNBT.setBoolean(itemStack, it.first, it.second)}
        meta = itemStack.itemMeta
        return this as T
    }

    open fun removeNBT(vararg key: String) : T {
        itemStack.itemMeta = meta
        key.forEach{ItemNBT.removeTag(itemStack, it)}
        meta = itemStack.itemMeta
        return this as T
    }

    open fun build() : ItemStack {
        itemStack.itemMeta = meta
        return itemStack
    }

}