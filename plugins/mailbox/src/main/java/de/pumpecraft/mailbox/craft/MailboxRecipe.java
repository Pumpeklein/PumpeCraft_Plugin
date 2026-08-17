package de.pumpecraft.mailbox.craft;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.utils.Recipes;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * How players get a mailbox: an iron body around a chest, the red flag at its side and a fence as
 * post - the pattern in the grid looks like the finished object.
 *
 * <pre>
 * E E E    E iron ingot
 * E T R    T chest
 *   Z      R red dye (flag)
 *          Z wooden fence (post)
 * </pre>
 */
public final class MailboxRecipe {
    private static final NamespacedKey KEY =
        Objects.requireNonNull(NamespacedKey.fromString("pumpemailbox:mailbox"));

    private boolean registered;

    public void register() {
        registered = Recipes.register(KEY, recipe());
    }

    public void unregister() {
        if (registered) {
            Recipes.unregister(KEY);
            registered = false;
        }
    }

    public void unlock(Player player) {
        if (registered) {
            player.discoverRecipe(KEY);
        }
    }

    private ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(KEY, MailboxItems.create());
        recipe.shape(
            "EEE",
            "ETR",
            " Z "
        );
        recipe.setIngredient('E', Material.IRON_INGOT);
        recipe.setIngredient('T', Material.CHEST);
        recipe.setIngredient('R', Material.RED_DYE);
        recipe.setIngredient('Z', new RecipeChoice.MaterialChoice(Tag.WOODEN_FENCES));
        return recipe;
    }
}
