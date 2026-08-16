package de.pumpecraft.mailbox;

import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.HingeAnimator;
import org.bukkit.Location;
import org.bukkit.Sound;

/**
 * Gives the two hinges of the mailbox their meaning: the door opens and falls shut again, the flag
 * stands up while mail is waiting.
 */
public final class MailboxAnimations {
    private final MailboxSettings settings;
    private final HingeAnimator animator;

    public MailboxAnimations(MailboxSettings settings, HingeAnimator animator) {
        this.settings = settings;
        this.animator = animator;
    }

    public void openDoor(DisplayObject mailbox) {
        animator.cancel(mailbox, MailboxObject.DOOR);
        if (animator.rotate(mailbox, MailboxObject.DOOR, MailboxObject.DOOR_OPEN_DEGREES, settings.doorOpenTicks())) {
            play(mailbox, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.4F);
        }
    }

    public void closeDoor(DisplayObject mailbox) {
        animator.cancel(mailbox, MailboxObject.DOOR);
        if (animator.rotate(mailbox, MailboxObject.DOOR, 0.0F, settings.doorCloseTicks())) {
            play(mailbox, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.4F);
        }
    }

    public void flapDoor(DisplayObject mailbox) {
        openDoor(mailbox);
        animator.delay(mailbox, MailboxObject.DOOR, settings.doorHoldTicks(), () -> closeDoor(mailbox));
    }

    public void setFlag(DisplayObject mailbox, boolean raised) {
        float target = raised ? MailboxObject.FLAG_RAISED_DEGREES : 0.0F;
        if (animator.rotate(mailbox, MailboxObject.FLAG, target, settings.flagTicks())) {
            play(mailbox, Sound.BLOCK_LEVER_CLICK, raised ? 1.6F : 1.0F);
        }
    }

    private void play(DisplayObject mailbox, Sound sound, float pitch) {
        Location location = mailbox.location();
        if (location != null) {
            location.getWorld().playSound(location, sound, 0.7F, pitch);
        }
    }
}
