package de.pumpecraft.mailbox;

import de.pumpecraft.utils.objects.DisplayObjectType;
import de.pumpecraft.utils.objects.ObjectHinge;
import org.bukkit.Material;

/**
 * Everything the mailbox model needs to be described: its parts and the two hinges. The hinge
 * coordinates are the ones from the model files, so they can be re-read there after every change
 * in Blockbench.
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
        .build();

    public static final ObjectHinge DOOR = ObjectHinge.fromModel("door", 8.0D, 13.0D, 3.65D);
    public static final ObjectHinge FLAG = ObjectHinge.fromModel("flag", 14.85D, 19.0D, 8.0D);

    public static final float DOOR_OPEN_DEGREES = 95.0F;
    public static final float FLAG_RAISED_DEGREES = 165.0F;

    private MailboxObject() {
    }
}
