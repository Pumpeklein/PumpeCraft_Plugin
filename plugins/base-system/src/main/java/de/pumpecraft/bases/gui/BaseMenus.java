package de.pumpecraft.bases.gui;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.BaseEntry;
import de.pumpecraft.bases.base.BaseLike;
import de.pumpecraft.bases.base.BaseService;
import de.pumpecraft.bases.base.BaseSort;
import de.pumpecraft.bases.base.BaseVisitor;
import de.pumpecraft.bases.base.PlayerBase;
import de.pumpecraft.bases.gui.BaseHolder.ClickTarget;
import de.pumpecraft.utils.Menus;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BaseMenus {
    private static final int LIST_SIZE = 54;
    private static final int FIRST_ENTRY_SLOT = 9;
    private static final int LAST_ENTRY_SLOT = 44;
    private static final int PAGE_SIZE = LAST_ENTRY_SLOT - FIRST_ENTRY_SLOT + 1;
    private static final int LIST_LIMIT = 200;

    private final PumpeBaseSystemPlugin plugin;
    private final BaseService service;

    public BaseMenus(PumpeBaseSystemPlugin plugin, BaseService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void openMain(Player player) {
        service.lookup(player, player.getUniqueId(), base -> {
            BaseHolder holder = new BaseHolder(
                BaseView.MAIN,
                BaseText.title("Base-Menü"),
                36,
                player.getUniqueId(),
                player.getName(),
                base.map(List::of).orElse(List.of()),
                null,
                0
            );
            renderMain(player, holder, base.orElse(null));
            player.openInventory(holder.getInventory());
        });
    }

    public void openBrowse(Player player, BaseSort sort, int page) {
        boolean includePrivate = service.mayInspectPrivate(player);
        plugin.runAsync(
            player,
            () -> service.repository().browse(
                sort, includePrivate, player.getUniqueId(), service.settings().browseLimit()),
            entries -> {
                BaseHolder holder = new BaseHolder(
                    BaseView.BROWSE,
                    BaseText.title("Basen · " + sort.displayName()),
                    LIST_SIZE,
                    player.getUniqueId(),
                    player.getName(),
                    entries,
                    sort,
                    clampPage(page, entries.size())
                );
                renderBrowse(holder);
                player.openInventory(holder.getInventory());
            }
        );
    }

    public void openDetail(Player player, UUID ownerId, String ownerName, BaseSort sort, int page) {
        plugin.runAsync(
            player,
            () -> {
                Optional<PlayerBase> base = ownerId != null
                    ? service.repository().baseOf(ownerId)
                    : service.repository().baseOf(ownerName);
                boolean liked = base.isPresent()
                    && service.repository().hasLiked(base.get().ownerId(), player.getUniqueId());
                return new Detail(base.orElse(null), liked);
            },
            detail -> {
                if (detail.base() == null) {
                    player.sendMessage(BaseText.error(
                        ownerName + " hat keine Base gesetzt."));
                    return;
                }
                if (!visible(player, detail.base())) {
                    player.sendMessage(BaseText.error("Diese Base ist privat."));
                    return;
                }
                BaseHolder holder = new BaseHolder(
                    BaseView.DETAIL,
                    BaseText.title("Base · " + detail.base().ownerName()),
                    27,
                    detail.base().ownerId(),
                    detail.base().ownerName(),
                    List.of(detail.base()),
                    sort,
                    page
                );
                renderDetail(player, holder, detail);
                player.openInventory(holder.getInventory());
            }
        );
    }

    public void openVisitors(Player player, int page) {
        plugin.runAsync(
            player,
            () -> service.repository().visitors(player.getUniqueId(), LIST_LIMIT),
            visitors -> {
                BaseHolder holder = new BaseHolder(
                    BaseView.VISITORS,
                    BaseText.title("Besucher deiner Base"),
                    LIST_SIZE,
                    player.getUniqueId(),
                    player.getName(),
                    visitors,
                    null,
                    clampPage(page, visitors.size())
                );
                renderVisitors(holder);
                player.openInventory(holder.getInventory());
            }
        );
    }

    public void openLikes(Player player, int page) {
        plugin.runAsync(
            player,
            () -> service.repository().likes(player.getUniqueId(), LIST_LIMIT),
            likes -> {
                BaseHolder holder = new BaseHolder(
                    BaseView.LIKES,
                    BaseText.title("Likes deiner Base"),
                    LIST_SIZE,
                    player.getUniqueId(),
                    player.getName(),
                    likes,
                    null,
                    clampPage(page, likes.size())
                );
                renderLikes(holder);
                player.openInventory(holder.getInventory());
            }
        );
    }

    public void openDeleteConfirm(Player player) {
        BaseHolder holder = new BaseHolder(
            BaseView.DELETE_CONFIRM,
            BaseText.title("Base löschen?"),
            27,
            player.getUniqueId(),
            player.getName(),
            List.of(),
            null,
            0
        );
        Inventory inventory = holder.getInventory();
        Menus.fill(inventory);
        put(holder, 11, Menus.item(Material.LIME_CONCRETE,
            Menus.text("Ja, Base löschen", NamedTextColor.GREEN),
            List.of(Menus.text("Dieser Schritt lässt sich nicht rückgängig machen.",
                NamedTextColor.GRAY))),
            ClickTarget.of(MenuAction.CONFIRM_DELETE));
        put(holder, 13, Menus.item(Material.TNT,
            Menus.text("Base und Statistiken löschen", NamedTextColor.RED),
            List.of(
                Menus.text("Position, Besuche und Likes verschwinden.", NamedTextColor.GRAY),
                Menus.text("Eine neue Base fängt bei null an.", NamedTextColor.DARK_GRAY))),
            ClickTarget.NONE);
        put(holder, 15, Menus.item(Material.RED_CONCRETE,
            Menus.text("Abbrechen", NamedTextColor.RED),
            List.of(Menus.text("Zurück zum Menü", NamedTextColor.GRAY))),
            ClickTarget.of(MenuAction.OPEN_MAIN));
        // Der Aufruf kommt aus einem Klick im offenen Menü; ein Wechsel im selben Tick lässt
        // den Client mit dem alten Fenster zurück.
        plugin.runSync(() -> player.openInventory(holder.getInventory()));
    }

    public void click(Player player, BaseHolder holder, int slot) {
        ClickTarget target = holder.target(slot);
        switch (target.action()) {
            case NONE -> {
            }
            case CLOSE -> player.closeInventory();
            case OPEN_MAIN -> openMain(player);
            case OPEN_BROWSE -> openBrowse(
                player, holder.sort() == null ? BaseSort.LIKES : holder.sort(), holder.page());
            case OPEN_VISITORS -> openVisitors(player, 0);
            case OPEN_LIKES -> openLikes(player, 0);
            case OPEN_DETAIL -> openDetail(
                player, target.playerId(), target.playerName(), holder.sort(), holder.page());
            case SET_BASE -> {
                if (!allowed(player, "base-set")) {
                    return;
                }
                service.setBase(player, null, base -> openMain(player));
            }
            case TOGGLE_VISIBILITY -> {
                if (!allowed(player, "base-set")) {
                    return;
                }
                service.toggleVisibility(player, base -> openMain(player));
            }
            case VISIT -> {
                if (!allowed(player, "base-visit")) {
                    return;
                }
                player.closeInventory();
                service.visit(player, target.playerId(), null);
            }
            case TOGGLE_LIKE -> {
                if (!allowed(player, "base-like")) {
                    return;
                }
                service.toggleLike(player, target.playerId(), outcome -> openDetail(
                    player, target.playerId(), target.playerName(), holder.sort(), holder.page()));
            }
            case ASK_DELETE -> {
                if (!allowed(player, "base-set")) {
                    return;
                }
                openDeleteConfirm(player);
            }
            case CONFIRM_DELETE -> {
                if (!allowed(player, "base-set")) {
                    return;
                }
                service.delete(player, deleted -> openMain(player));
            }
            case CYCLE_SORT -> openBrowse(player, holder.sort().next(), 0);
            case PAGE_PREVIOUS -> turnPage(player, holder, -1);
            case PAGE_NEXT -> turnPage(player, holder, 1);
        }
    }

    private void turnPage(Player player, BaseHolder holder, int direction) {
        int page = clampPage(holder.page() + direction, holder.entries().size());
        if (page == holder.page()) {
            return;
        }
        holder.page(page);
        switch (holder.view()) {
            case BROWSE -> renderBrowse(holder);
            case VISITORS -> renderVisitors(holder);
            case LIKES -> renderLikes(holder);
            default -> {
                return;
            }
        }
        player.updateInventory();
    }

    private void renderMain(Player player, BaseHolder holder, PlayerBase base) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.fill(inventory);
        boolean mayManage = player.hasPermission(plugin.permission("base-set"));

        put(holder, 11, baseCard(base, player, mayManage), base == null
            ? ClickTarget.of(mayManage ? MenuAction.SET_BASE : MenuAction.NONE)
            : ClickTarget.of(MenuAction.VISIT, player.getUniqueId(), player.getName()));

        if (mayManage) {
            put(holder, 13, Menus.item(Material.COMPASS,
                Menus.text(base == null ? "Base hier setzen" : "Base hierher verschieben",
                    NamedTextColor.AQUA),
                List.of(
                    Menus.label("Deine Position: ", coordinates(player), NamedTextColor.WHITE),
                    Menus.text("Welt: " + player.getWorld().getName(), NamedTextColor.DARK_GRAY),
                    Component.empty(),
                    Menus.text(base == null
                        ? "Neue Basen sind " + (service.settings().defaultPublic()
                            ? "öffentlich." : "privat.")
                        : "Die Sichtbarkeit bleibt erhalten.", NamedTextColor.GRAY),
                    Menus.action("Klicken zum Setzen"))),
                ClickTarget.of(MenuAction.SET_BASE));
            put(holder, 15, visibilityItem(base),
                base == null ? ClickTarget.NONE : ClickTarget.of(MenuAction.TOGGLE_VISIBILITY));
        }

        put(holder, 20, Menus.item(Material.ENDER_EYE,
            Menus.text("Besucher", NamedTextColor.LIGHT_PURPLE),
            List.of(
                Menus.label("Besuche gesamt: ",
                    base == null ? "0" : Texts.number(base.visitCount()), NamedTextColor.WHITE),
                Menus.label("Unterschiedliche Spieler: ",
                    base == null ? "0" : Texts.number(base.uniqueVisitors()), NamedTextColor.WHITE),
                Component.empty(),
                Menus.action("Klicken für die Liste"))),
            ClickTarget.of(MenuAction.OPEN_VISITORS));

        put(holder, 22, Menus.item(Material.HEART_OF_THE_SEA,
            Menus.text("Likes", NamedTextColor.YELLOW),
            List.of(
                Menus.label("Likes: ",
                    base == null ? "0" : Texts.number(base.likeCount()), NamedTextColor.YELLOW),
                Component.empty(),
                Menus.action("Klicken für die Liste"))),
            ClickTarget.of(MenuAction.OPEN_LIKES));

        put(holder, 24, Menus.item(Material.FILLED_MAP,
            Menus.text("Alle Basen", NamedTextColor.GOLD),
            List.of(
                Menus.text("Öffentliche Basen durchstöbern,", NamedTextColor.GRAY),
                Menus.text("besuchen und liken.", NamedTextColor.GRAY),
                Component.empty(),
                Menus.action("Klicken zum Öffnen"))),
            ClickTarget.of(MenuAction.OPEN_BROWSE));

        if (base != null && mayManage) {
            put(holder, 29, Menus.item(Material.TNT,
                Menus.text("Base löschen", NamedTextColor.RED),
                List.of(
                    Menus.text("Entfernt Position, Besuche und Likes.", NamedTextColor.GRAY),
                    Menus.action("Klicken - danach folgt eine Rückfrage"))),
                ClickTarget.of(MenuAction.ASK_DELETE));
        }
        put(holder, 33, closeItem(), ClickTarget.of(MenuAction.CLOSE));
    }

    private void renderBrowse(BaseHolder holder) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        List<?> entries = holder.entries();
        put(holder, 0, backItem("Zum Base-Menü"), ClickTarget.of(MenuAction.OPEN_MAIN));
        put(holder, 4, Menus.item(Material.FILLED_MAP,
            Menus.text("Alle Basen", NamedTextColor.GOLD),
            List.of(
                Menus.label("Gefunden: ", Texts.number(entries.size()), NamedTextColor.WHITE),
                Menus.label("Sortierung: ", holder.sort().displayName(), NamedTextColor.AQUA),
                Menus.label("Seite: ", pageLabel(holder), NamedTextColor.WHITE))),
            ClickTarget.NONE);
        put(holder, 8, closeItem(), ClickTarget.of(MenuAction.CLOSE));

        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            BaseEntry entry = (BaseEntry) entries.get(offset + index);
            List<Component> lore = new ArrayList<>();
            lore.add(entry.publicBase()
                ? Menus.text("Öffentlich", NamedTextColor.GREEN)
                : Menus.text("Privat", NamedTextColor.RED));
            lore.add(Menus.label("Welt: ", entry.worldName(), NamedTextColor.WHITE));
            lore.add(Menus.label("Besuche: ",
                Texts.number(entry.visitCount()), NamedTextColor.WHITE));
            lore.add(Menus.label("Likes: ",
                Texts.number(entry.likeCount()), NamedTextColor.YELLOW));
            lore.add(Menus.label("Aktualisiert: ",
                service.relativeTime(entry.updatedAt()), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Menus.action("Klicken für Details"));
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(entry.ownerId(),
                    Menus.text(entry.ownerName(), NamedTextColor.GOLD), lore),
                ClickTarget.of(MenuAction.OPEN_DETAIL, entry.ownerId(), entry.ownerName()));
        }

        putPaging(holder);
        put(holder, 49, Menus.item(holder.sort().icon(),
            Menus.text("Sortierung: " + holder.sort().displayName(), NamedTextColor.AQUA),
            List.of(
                Menus.text("Als Nächstes: " + holder.sort().next().displayName(),
                    NamedTextColor.GRAY),
                Menus.action("Klicken zum Wechseln"))),
            ClickTarget.of(MenuAction.CYCLE_SORT));
    }

    private void renderVisitors(BaseHolder holder) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        List<?> entries = holder.entries();
        put(holder, 0, backItem("Zum Base-Menü"), ClickTarget.of(MenuAction.OPEN_MAIN));
        put(holder, 4, Menus.item(Material.ENDER_EYE,
            Menus.text("Besucher deiner Base", NamedTextColor.LIGHT_PURPLE),
            List.of(
                Menus.label("Spieler: ", Texts.number(entries.size()), NamedTextColor.WHITE),
                Menus.label("Seite: ", pageLabel(holder), NamedTextColor.WHITE),
                Component.empty(),
                Menus.text(entries.isEmpty()
                    ? "Noch war niemand bei dir."
                    : "Sortiert nach Anzahl der Besuche.", NamedTextColor.GRAY))),
            ClickTarget.NONE);
        put(holder, 8, closeItem(), ClickTarget.of(MenuAction.CLOSE));

        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            BaseVisitor visitor = (BaseVisitor) entries.get(offset + index);
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(visitor.playerId(),
                    Menus.text(visitor.playerName(), NamedTextColor.WHITE),
                    List.of(
                        Menus.label("Besuche: ",
                            Texts.number(visitor.visitCount()), NamedTextColor.WHITE),
                        Menus.label("Zuletzt: ",
                            service.relativeTime(visitor.lastVisitedAt()), NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Menus.action("Klicken: Base dieses Spielers"))),
                ClickTarget.of(MenuAction.OPEN_DETAIL, visitor.playerId(), visitor.playerName()));
        }
        putPaging(holder);
    }

    private void renderLikes(BaseHolder holder) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        List<?> entries = holder.entries();
        put(holder, 0, backItem("Zum Base-Menü"), ClickTarget.of(MenuAction.OPEN_MAIN));
        put(holder, 4, Menus.item(Material.HEART_OF_THE_SEA,
            Menus.text("Likes deiner Base", NamedTextColor.YELLOW),
            List.of(
                Menus.label("Likes: ", Texts.number(entries.size()), NamedTextColor.YELLOW),
                Menus.label("Seite: ", pageLabel(holder), NamedTextColor.WHITE),
                Component.empty(),
                Menus.text(entries.isEmpty()
                    ? "Noch hat niemand deine Base geliked."
                    : "Neueste zuerst.", NamedTextColor.GRAY))),
            ClickTarget.NONE);
        put(holder, 8, closeItem(), ClickTarget.of(MenuAction.CLOSE));

        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            BaseLike like = (BaseLike) entries.get(offset + index);
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(like.playerId(),
                    Menus.text(like.playerName(), NamedTextColor.YELLOW),
                    List.of(
                        Menus.label("Geliked: ",
                            service.relativeTime(like.createdAt()), NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Menus.action("Klicken: Base dieses Spielers"))),
                ClickTarget.of(MenuAction.OPEN_DETAIL, like.playerId(), like.playerName()));
        }
        putPaging(holder);
    }

    private void renderDetail(Player player, BaseHolder holder, Detail detail) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.fill(inventory);
        PlayerBase base = detail.base();
        boolean ownBase = base.ownerId().equals(player.getUniqueId());

        put(holder, 11, Menus.item(Material.ENDER_PEARL,
            Menus.text("Besuchen", NamedTextColor.AQUA),
            List.of(
                Menus.text(base.bukkitLocation() == null
                    ? "Die Welt dieser Base ist nicht geladen."
                    : "Teleport zur Base von " + base.ownerName() + ".", NamedTextColor.GRAY),
                Menus.action("Klicken zum Teleportieren"))),
            ClickTarget.of(MenuAction.VISIT, base.ownerId(), base.ownerName()));

        put(holder, 13, baseCard(base, player, false), ClickTarget.NONE);

        if (ownBase) {
            put(holder, 15, Menus.item(Material.GRAY_DYE,
                Menus.text("Eigene Base", NamedTextColor.GRAY),
                List.of(Menus.text("Die eigene Base lässt sich nicht liken.",
                    NamedTextColor.DARK_GRAY))),
                ClickTarget.NONE);
        } else {
            put(holder, 15, Menus.item(
                detail.liked() ? Material.GRAY_DYE : Material.HEART_OF_THE_SEA,
                Menus.text(detail.liked() ? "Like zurückziehen" : "Base liken",
                    detail.liked() ? NamedTextColor.GRAY : NamedTextColor.YELLOW),
                List.of(
                    Menus.label("Likes: ",
                        Texts.number(base.likeCount()), NamedTextColor.YELLOW),
                    Component.empty(),
                    Menus.action("Klicken zum Umschalten"))),
                ClickTarget.of(MenuAction.TOGGLE_LIKE, base.ownerId(), base.ownerName()));
        }

        put(holder, 18, backItem(holder.sort() == null ? "Zum Base-Menü" : "Zur Basen-Liste"),
            ClickTarget.of(holder.sort() == null ? MenuAction.OPEN_MAIN : MenuAction.OPEN_BROWSE));
        put(holder, 26, closeItem(), ClickTarget.of(MenuAction.CLOSE));
    }

    private void putPaging(BaseHolder holder) {
        int pages = pageCount(holder.entries().size());
        if (holder.page() > 0) {
            put(holder, 45, Menus.item(Material.ARROW,
                Menus.text("Vorherige Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + holder.page() + " von " + pages,
                    NamedTextColor.GRAY))),
                ClickTarget.of(MenuAction.PAGE_PREVIOUS));
        }
        if (holder.page() + 1 < pages) {
            put(holder, 53, Menus.item(Material.ARROW,
                Menus.text("Nächste Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + (holder.page() + 2) + " von " + pages,
                    NamedTextColor.GRAY))),
                ClickTarget.of(MenuAction.PAGE_NEXT));
        }
    }

    private ItemStack baseCard(PlayerBase base, Player viewer, boolean mayManage) {
        if (base == null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Menus.text("Du hast noch keine Base gesetzt.", NamedTextColor.GRAY));
            if (mayManage) {
                lore.add(Component.empty());
                lore.add(Menus.action("Klicken: hier setzen"));
            }
            return Menus.item(Material.RED_BED,
                Menus.text("Keine Base", NamedTextColor.RED), lore);
        }
        boolean ownBase = base.ownerId().equals(viewer.getUniqueId());
        boolean showCoordinates = base.publicBase() || ownBase || service.mayInspectPrivate(viewer);
        List<Component> lore = new ArrayList<>();
        lore.add(base.publicBase()
            ? Menus.text("Öffentlich", NamedTextColor.GREEN)
            : Menus.text("Privat", NamedTextColor.RED));
        lore.add(Menus.label("Welt: ", base.location().worldName(), NamedTextColor.WHITE));
        lore.add(showCoordinates
            ? Menus.label("Position: ", blockCoordinates(base), NamedTextColor.WHITE)
            : Menus.text("Position: verborgen", NamedTextColor.DARK_GRAY));
        lore.add(Menus.label("Besuche: ",
            Texts.number(base.visitCount()), NamedTextColor.WHITE));
        lore.add(Menus.label("Unterschiedliche Spieler: ",
            Texts.number(base.uniqueVisitors()), NamedTextColor.WHITE));
        lore.add(Menus.label("Likes: ", Texts.number(base.likeCount()), NamedTextColor.YELLOW));
        lore.add(Menus.label("Gesetzt: ",
            service.relativeTime(base.createdAt()), NamedTextColor.DARK_GRAY));
        if (ownBase) {
            lore.add(Component.empty());
            lore.add(Menus.action("Klicken: zur eigenen Base"));
        }
        return Menus.head(base.ownerId(),
            Menus.text("Base von " + base.ownerName(), NamedTextColor.GOLD), lore);
    }

    private ItemStack visibilityItem(PlayerBase base) {
        if (base == null) {
            return Menus.item(Material.GRAY_DYE,
                Menus.text("Sichtbarkeit", NamedTextColor.GRAY),
                List.of(Menus.text("Erst eine Base setzen.", NamedTextColor.DARK_GRAY)));
        }
        return Menus.item(base.publicBase() ? Material.LIME_DYE : Material.RED_DYE,
            Menus.text(base.publicBase() ? "Sichtbarkeit: Öffentlich" : "Sichtbarkeit: Privat",
                base.publicBase() ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(
                Menus.text(base.publicBase()
                    ? "Alle dürfen dich besuchen und liken."
                    : "Nur du und das Team kommen hierher.", NamedTextColor.GRAY),
                Component.empty(),
                Menus.action("Klicken zum Umschalten")));
    }

    private ItemStack backItem(String description) {
        return Menus.item(Material.ARROW,
            Menus.text("Zurück", NamedTextColor.YELLOW),
            List.of(Menus.text(description, NamedTextColor.GRAY)));
    }

    private ItemStack closeItem() {
        return Menus.item(Material.BARRIER,
            Menus.text("Schließen", NamedTextColor.RED), List.of());
    }

    private void put(
        BaseHolder holder,
        int slot,
        ItemStack item,
        ClickTarget target
    ) {
        holder.getInventory().setItem(slot, item);
        holder.bind(slot, target);
    }

    private boolean allowed(Player player, String permissionKey) {
        if (player.hasPermission(plugin.permission(permissionKey))) {
            return true;
        }
        player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
        return false;
    }

    private boolean visible(Player viewer, PlayerBase base) {
        return base.publicBase()
            || base.ownerId().equals(viewer.getUniqueId())
            || service.mayInspectPrivate(viewer);
    }

    private String pageLabel(BaseHolder holder) {
        return (holder.page() + 1) + " / " + pageCount(holder.entries().size());
    }

    private int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int clampPage(int page, int size) {
        return Math.max(0, Math.min(page, pageCount(size) - 1));
    }

    private String coordinates(Player player) {
        return Math.round(player.getLocation().getX()) + " / "
            + Math.round(player.getLocation().getY()) + " / "
            + Math.round(player.getLocation().getZ());
    }

    private String blockCoordinates(PlayerBase base) {
        return Math.round(base.location().x()) + " / "
            + Math.round(base.location().y()) + " / "
            + Math.round(base.location().z());
    }

    private record Detail(PlayerBase base, boolean liked) {
    }
}
