package de.pumpecraft.mailbox;

import de.pumpecraft.utils.objects.DisplayObjectType;
import de.pumpecraft.utils.objects.ObjectHinge;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Everything the mailbox model needs to be described: its parts, the two hinges and the parcel
 * that appears next to it. The hinge coordinates are the ones from the model files, so they can be
 * re-read there after every change in Blockbench.
 */
public final class MailboxObject {
    public static final DisplayObjectType TYPE = DisplayObjectType.builder("mailbox")
        .baseMaterial(Material.PAPER)
        .itemModel("pumpecraft:mailbox")
        .bodyModel("pumpecraft:mailbox_body")
        .part("door", "pumpecraft:mailbox_door")
        .part("flag", "pumpecraft:mailbox_flag")
        .hitbox(0.7F, 1.4F)
        .shadow(0.5F)
        .stackable(false)
        .label(1.7F, 0.16F)
        .build();

    public static final ObjectHinge DOOR = ObjectHinge.fromModel("door", 8.0D, 13.0D, 3.65D);
    public static final ObjectHinge FLAG = ObjectHinge.fromModel("flag", 14.85D, 19.0D, 8.0D);

    public static final float DOOR_OPEN_DEGREES = 95.0F;
    public static final float FLAG_RAISED_DEGREES = 165.0F;

    public static final String PARCEL_PART = "parcel";
    public static final NamespacedKey PARCEL_MODEL =
        Objects.requireNonNull(NamespacedKey.fromString("pumpecraft:mailbox_parcel"));
    public static final double PARCEL_OFFSET_SIDE = -0.6D;
    public static final double PARCEL_OFFSET_FRONT = 0.15D;

    private MailboxObject() {
    }
}
