package com.github.drakescraft_labs.galactifun.base.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import com.github.drakescraft_labs.galactifun.Galactifun;
import com.github.drakescraft_labs.galactifun.api.worlds.AlienWorld;
import com.github.drakescraft_labs.galactifun.base.BaseItems;
import com.github.drakescraft_labs.galactifun.base.items.StargateGeometry.Orientation;
import com.github.drakescraft_labs.galactifun.base.items.StargateGeometry.Pos;
import com.github.drakescraft_labs.galactifun.util.BSUtils;
import dev.drake.infinitylib.common.Events;
import dev.drake.infinitylib.common.Scheduler;
import dev.drake.infinitylib.machines.MenuBlock;
import com.github.drakescraft_labs.slimefun4.api.events.PlayerRightClickEvent;
import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockBreakHandler;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockPlaceHandler;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockUseHandler;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.libraries.dough.items.CustomItemStack;
import com.github.drakescraft_labs.slimefun4.utils.ChatUtils;
import com.github.drakescraft_labs.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Controlador del Stargate.
 *
 * <p>QUE HACE: valida que el anillo este armado (en cualquiera de los dos planos), enciende/apaga el portal, guarda
 * direccion, nombre y destino, y teletransporta a quien cruza.
 *
 * <p>QUE CAMBIO (2026-10-14) Y POR QUE: los jugadores no entendian por que "no funciona" (ver la conversacion de
 * Discord de Galactifun: "you step in and nothing happens", "teleports me to the roof", "Destination is Blocked",
 * "it needs to be aligned along the z axis", "its the capacitor in front"). Causas reales en el codigo original:
 * <ul>
 *   <li>solo se aceptaba el anillo en el plano del eje Z y el aviso no decia por que fallaba;</li>
 *   <li>sin destino guardado el evento NO se cancelaba y el portal vanilla te mandaba encima del controlador
 *       ("te sube al techo");</li>
 *   <li>la llegada era SIEMPRE el bloque x+1 del controlador de destino: cualquier maquina o capacitor ahi daba
 *       "destination is blocked" sin decir que lo bloqueaba;</li>
 *   <li>la direccion (hash hexadecimal) solo existia si alguien habia abierto el menu antes, y el destino se
 *       escribia a mano; no habia nombres ni lista.</li>
 * </ul>
 */
public final class StargateController extends SlimefunItem implements Listener {

    private static final int GATEWAY_TICKS = 201;
    private static final int MAX_NAME = 24;
    private static final int PICKER_PAGE = 45;

    // menu principal (3 filas)
    private static final int SLOT_STATUS = 10;
    private static final int SLOT_ADDRESS = 11;
    private static final int SLOT_DESTINATION = 12;
    private static final int SLOT_LINK_BACK = 13;
    private static final int SLOT_HELP = 14;
    private static final int SLOT_DEACTIVATE = 16;

    public StargateController(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        Events.registerListener(this);

        addItemHandler((BlockUseHandler) e -> e.getClickedBlock().ifPresent(b -> onUse(e, e.getPlayer(), b)));

        // La direccion se asigna al colocar el controlador, no al abrir el menu por primera vez.
        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                ensureAddress(e.getBlock());
            }
        });

        addItemHandler(new BlockBreakHandler(true, true) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent e, ItemStack item, List<ItemStack> drops) {
                if (Boolean.parseBoolean(BlockStorage.getLocationInfo(e.getBlock().getLocation(), "locked"))) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(ChatColor.RED + "Deactivate the Stargate before destroying it.");
                }
            }
        });
    }

    // ------------------------------------------------------------------ estructura

    private static Block at(Block controller, Orientation o, Pos pos) {
        int[] d = o.offset(pos.y(), pos.h());
        return controller.getRelative(d[0], d[1], d[2]);
    }

    private static boolean isRing(Block b) {
        return BlockStorage.check(b) instanceof StargateRing;
    }

    /** Orientacion en la que el anillo esta completo, si lo esta en alguna. */
    @Nonnull
    public static Optional<Orientation> detectOrientation(@Nonnull Block controller) {
        for (Orientation o : Orientation.values()) {
            if (StargateGeometry.faltantes(pos -> isRing(at(controller, o, pos))).isEmpty()) {
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    public static boolean isPartOfStargate(@Nonnull Block b) {
        return detectOrientation(b).isPresent();
    }

    @Nonnull
    public static Optional<List<Block>> getRingBlocks(@Nonnull Block b) {
        return detectOrientation(b).map(o -> {
            List<Block> rings = new ArrayList<>();
            for (Pos pos : StargateGeometry.ANILLO) {
                rings.add(at(b, o, pos));
            }
            return rings;
        });
    }

    /** Bloques del portal si esta encendido (los 21 son END_GATEWAY) en alguna orientacion. */
    @Nonnull
    public static Optional<List<Block>> getPortalBlocks(@Nonnull Block b) {
        for (Orientation o : Orientation.values()) {
            List<Block> portals = new ArrayList<>();
            boolean ok = true;
            for (Pos pos : StargateGeometry.PORTAL) {
                Block portal = at(b, o, pos);
                if (portal.getType() == Material.END_GATEWAY) {
                    portals.add(portal);
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return Optional.of(portals);
            }
        }
        return Optional.empty();
    }

    public static void lockBlocks(Block controller, boolean lock) {
        String data = Boolean.toString(lock);
        getRingBlocks(controller).ifPresent(l -> l.forEach(b -> BlockStorage.addBlockInfo(b, "locked", data)));
        getPortalBlocks(controller).ifPresent(l -> l.forEach(b -> BlockStorage.addBlockInfo(b, "locked", data)));
    }

    /** Explica al jugador POR QUE el Stargate no esta armado y marca con particulas lo que falta. */
    private static void explainNotAssembled(Player p, Block controller) {
        Orientation best = null;
        List<Pos> bestMissing = null;
        for (Orientation o : Orientation.values()) {
            List<Pos> missing = StargateGeometry.faltantes(pos -> isRing(at(controller, o, pos)));
            if (bestMissing == null || missing.size() < bestMissing.size()) {
                best = o;
                bestMissing = missing;
            }
        }

        int total = StargateGeometry.ANILLO.size();
        p.sendMessage(ChatColor.RED + "The Stargate is not assembled: on the " + best.nombre() + " plane " + bestMissing.size() + " of "
                + total + " Stargate Rings are missing.");
        p.sendMessage(ChatColor.GRAY + "The controller goes at the BASE of the arch, in the center (between two rings). The arch "
                + "is 7 tall and 7 wide and must stand vertical. Either the X axis or the Z axis is accepted.");

        int shown = 0;
        for (Pos pos : bestMissing) {
            Block spot = at(controller, best, pos);
            if (shown++ < 3) {
                String ocupado = spot.getType().isAir() ? "it is empty" : "there is " + describeBlock(spot);
                p.sendMessage(ChatColor.YELLOW + " - Missing Stargate Ring at " + spot.getX() + " " + spot.getY() + " "
                        + spot.getZ() + " (" + StargateGeometry.describir(pos) + "): " + ocupado);
            }
            p.spawnParticle(Particle.END_ROD, spot.getLocation().add(0.5, 0.5, 0.5), 6, 0.15, 0.15, 0.15, 0.0);
        }
        if (bestMissing.size() > 3) {
            p.sendMessage(ChatColor.GRAY + "   ... and " + (bestMissing.size() - 3) + " more (marked with particles).");
        }
    }

    private static String describeBlock(Block b) {
        SlimefunItem sf = BlockStorage.check(b);
        return sf != null ? ChatColor.stripColor(sf.getItemName()) : b.getType().name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ uso

    private void onUse(PlayerRightClickEvent event, Player p, Block b) {
        Optional<Orientation> orientation = detectOrientation(b);
        if (orientation.isEmpty()) {
            explainNotAssembled(p, b);
            return;
        }
        event.cancel();
        ensureAddress(b);

        if (getPortalBlocks(b).isEmpty()) {
            activate(p, b, orientation.get());
            return;
        }

        getMenu(b, p).open(p);
    }

    private void activate(Player p, Block b, Orientation o) {
        // No se pisan maquinas ni bloques de Slimefun: el interior del arco debe estar libre.
        for (Pos pos : StargateGeometry.PORTAL) {
            Block inner = at(b, o, pos);
            if (BlockStorage.check(inner) != null) {
                p.sendMessage(ChatColor.RED + "Cannot activate: " + describeBlock(inner) + " is inside the arch ("
                        + inner.getX() + " " + inner.getY() + " " + inner.getZ() + "). Remove it to clear the inside.");
                return;
            }
        }

        for (Pos pos : StargateGeometry.PORTAL) {
            Block portal = at(b, o, pos);
            portal.setType(Material.END_GATEWAY);
            EndGateway gateway = (EndGateway) portal.getState();
            gateway.setAge(GATEWAY_TICKS);
            gateway.setExitLocation(b.getLocation());
            gateway.update(false, false);
        }

        BlockStorage.addBlockInfo(b, "lit", "true");
        String destAddress = BlockStorage.getLocationInfo(b.getLocation(), "destination");
        if (destAddress != null) {
            setDestination(destAddress, b, p);
        }

        lockBlocks(b, true);
        p.sendMessage(ChatColor.YELLOW + "Stargate activated."
                + (destAddress == null ? ChatColor.GRAY + " It has no destination yet: right-click the controller and pick one." : ""));
    }

    private static void deactivate(Block b) {
        getPortalBlocks(b).ifPresent(li -> {
            for (Block block : li) {
                block.setType(Material.AIR);
                BlockStorage.clearBlockInfo(block);
            }
        });
        lockBlocks(b, false);
        BlockStorage.addBlockInfo(b, "lit", "false");
    }

    // ------------------------------------------------------------------ direccion, nombre, destino

    @Nonnull
    private static String ensureAddress(@Nonnull Block b) {
        Location l = b.getLocation();
        String address = BlockStorage.getLocationInfo(l, "gfsgAddress");
        if (address == null) {
            String lString = String.format("%s-%d-%d-%d", b.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            address = Integer.toHexString(lString.hashCode());
            BlockStorage.addBlockInfo(b, "gfsgAddress", address);
        }
        return address;
    }

    private static String nameOf(Location l) {
        String name = BlockStorage.getLocationInfo(l, "gfsgName");
        return name == null || name.isBlank() ? null : name;
    }

    /** Un portal conocido: direccion, nombre opcional, ubicacion y si esta encendido. */
    private record Gate(String address, @Nullable String name, Location location, boolean lit) {
        String label() {
            return name != null ? name + " (" + address + ")" : address;
        }
    }

    private static List<Gate> allGates() {
        List<Gate> gates = new ArrayList<>();
        for (BlockStorage storage : Slimefun.getRegistry().getWorlds().values()) {
            for (Map.Entry<Location, Config> entry : storage.getRawStorage().entrySet()) {
                String address = entry.getValue().getString("gfsgAddress");
                if (address == null) continue;
                String name = entry.getValue().getString("gfsgName");
                boolean lit = Boolean.parseBoolean(entry.getValue().getString("lit"));
                gates.add(new Gate(address, name == null || name.isBlank() ? null : name, entry.getKey(), lit));
            }
        }
        return gates;
    }

    /** Busca un portal por direccion exacta o por nombre (sin distinguir mayusculas). */
    @Nullable
    private static Gate findGate(@Nonnull String query) {
        String q = query.trim();
        for (Gate g : allGates()) {
            if (g.address().equalsIgnoreCase(q) || (g.name() != null && g.name().equalsIgnoreCase(q))) {
                return g;
            }
        }
        return null;
    }

    private static void setDestination(String destination, Block b, Player p) {
        Gate dest = findGate(destination);
        if (dest == null) {
            p.sendMessage(ChatColor.RED + "No Stargate has the address or name \"" + destination
                    + "\". Use the destination button to pick one from the list.");
            return;
        }
        if (dest.location().equals(b.getLocation())) {
            p.sendMessage(ChatColor.RED + "A Stargate cannot target itself.");
            return;
        }

        BSUtils.setStoredLocation(b.getLocation(), "dest", dest.location());
        BlockStorage.addBlockInfo(b, "destination", dest.address());

        p.sendMessage(ChatColor.YELLOW + String.format("Destination set: %s at %d %d %d (%s).", dest.label(),
                dest.location().getBlockX(), dest.location().getBlockY(), dest.location().getBlockZ(),
                dest.location().getWorld().getName()));
        if (!dest.lit()) {
            p.sendMessage(ChatColor.GRAY + "Note: that Stargate is off; it must be activated (right-click its controller) "
                    + "before you can arrive.");
        }
    }

    /** Hace que el Stargate de destino apunte de vuelta a este (para viajes de ida y vuelta). */
    private static void linkBack(Block self, Player p) {
        String destAddress = BlockStorage.getLocationInfo(self.getLocation(), "destination");
        Gate dest = destAddress == null ? null : findGate(destAddress);
        if (dest == null) {
            p.sendMessage(ChatColor.RED + "Pick a valid destination first.");
            return;
        }
        String myAddress = ensureAddress(self);
        BSUtils.setStoredLocation(dest.location(), "dest", self.getLocation());
        BlockStorage.addBlockInfo(dest.location(), "destination", myAddress);
        p.sendMessage(ChatColor.YELLOW + dest.label() + " now points back to this Stargate.");
    }

    // ------------------------------------------------------------------ menus

    @Nonnull
    private ChestMenu getMenu(@Nonnull Block b, @Nonnull Player viewer) {
        ChestMenu menu = new ChestMenu(this.getItemName());
        for (int i = 0; i < 27; i++) {
            menu.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        Location l = b.getLocation();
        String address = ensureAddress(b);
        String name = nameOf(l);
        String destination = BlockStorage.getLocationInfo(l, "destination");
        Gate dest = destination == null ? null : findGate(destination);
        boolean lit = getPortalBlocks(b).isPresent();
        Optional<Orientation> orientation = detectOrientation(b);

        // Estado: todo lo que un jugador necesita saber para entender por que funciona o no
        List<String> status = new ArrayList<>();
        status.add(orientation.isPresent() ? "&aStructure: assembled (" + orientation.get().nombre() + ")"
                : "&cStructure: incomplete");
        status.add(lit ? "&aPortal: active" : "&cPortal: inactive");
        if (destination == null) {
            status.add("&cDestination: none (use the destination button)");
        } else if (dest == null) {
            status.add("&cDestination: " + destination + " (no longer exists)");
        } else {
            status.add("&aDestination: " + dest.label());
            status.add(dest.lit() ? "&aThe destination is active" : "&eThe destination is OFF: activate it to arrive");
            String back = BlockStorage.getLocationInfo(dest.location(), "destination");
            status.add(address.equalsIgnoreCase(back == null ? "" : back) ? "&aThe destination points back here"
                    : "&7The destination does not point back (use \"Link Return\")");
        }
        menu.addItem(SLOT_STATUS, new CustomItemStack(lit && dest != null ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                "&fStargate Status", status.toArray(new String[0])), ChestMenuUtils.getEmptyClickHandler());

        menu.addItem(SLOT_ADDRESS, new CustomItemStack(Material.BOOK,
                "&fAddress: " + address, name == null ? "&7Unnamed" : "&7Name: &f" + name,
                "", "&7Left click: send the address to chat",
                "&7Right click: give it an easy-to-remember name"), (p, i, s, c) -> {
            if (c.isRightClicked()) {
                p.sendMessage(ChatColor.YELLOW + "Type the Stargate name (max " + MAX_NAME + " characters, letters and numbers).");
                ChatUtils.awaitInput(p, st -> rename(b, st, p));
            } else {
                p.sendMessage(Component.text()
                        .color(NamedTextColor.YELLOW)
                        .content("Address (click to copy): " + address)
                        .clickEvent(ClickEvent.copyToClipboard(address))
                        .build());
            }
            p.closeInventory();
            return false;
        });

        menu.addItem(SLOT_DESTINATION, new CustomItemStack(Material.COMPASS,
                "&fChoose Destination", "&7Current: " + (dest != null ? dest.label() : "none"),
                "", "&7Left click: list of known Stargates",
                "&7Right click: type an address or name"), (p, i, s, c) -> {
            if (c.isRightClicked()) {
                p.sendMessage(ChatColor.YELLOW + "Type the address or name of the destination Stargate.");
                ChatUtils.awaitInput(p, st -> setDestinationAndRefresh(st, b, p));
                p.closeInventory();
            } else {
                openPicker(b, p, 0);
            }
            return false;
        });

        menu.addItem(SLOT_LINK_BACK, new CustomItemStack(Material.ENDER_EYE,
                "&fLink Return", "&7Makes the destination point back to this Stargate", "&7(for round trips)"),
                (p, i, s, c) -> {
                    linkBack(b, p);
                    p.closeInventory();
                    return false;
                });

        menu.addItem(SLOT_HELP, new CustomItemStack(Material.KNOWLEDGE_BOOK,
                "&fHelp: how it works",
                "&71. Build the arch with 15 Stargate Rings: 7 tall and 7 wide,",
                "&7   with the controller at the center of the base.",
                "&72. Right-click the controller to activate the portal.",
                "&73. Choose a destination (another active Stargate).",
                "&74. The destination needs a free spot 1 wide and 2 tall",
                "&7   on EITHER side of its controller (keep machines away).",
                "&75. To come back, use \"Link Return\"."), ChestMenuUtils.getEmptyClickHandler());

        menu.addItem(SLOT_DEACTIVATE, new CustomItemStack(Material.BARRIER, "&cDeactivate the Stargate",
                "&7Required before breaking the arch"), (p, i, s, c) -> {
            deactivate(b);
            p.sendMessage(ChatColor.YELLOW + "Stargate deactivated.");
            p.closeInventory();
            return false;
        });

        return menu;
    }

    private static void rename(Block b, String raw, Player p) {
        String clean = raw.replaceAll("[^\\p{L}\\p{N} _\\-]", "").trim();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME).trim();
        if (clean.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Invalid name.");
            return;
        }
        Gate other = findGate(clean);
        if (other != null && !other.location().equals(b.getLocation())) {
            p.sendMessage(ChatColor.RED + "A Stargate with that name or address already exists.");
            return;
        }
        BlockStorage.addBlockInfo(b, "gfsgName", clean);
        p.sendMessage(ChatColor.YELLOW + "Stargate renamed to \"" + clean + "\".");
    }

    private static void setDestinationAndRefresh(String query, Block b, Player p) {
        setDestination(query, b, p);
    }

    /** Lista paginada de todos los Stargates conocidos, ordenados por cercania. */
    private void openPicker(Block b, Player p, int page) {
        Location here = b.getLocation();
        List<Gate> gates = new ArrayList<>();
        for (Gate g : allGates()) {
            if (!g.location().equals(here)) gates.add(g);
        }
        gates.sort(Comparator.comparingDouble((Gate g) -> g.location().getWorld().equals(here.getWorld())
                ? g.location().distanceSquared(here) : Double.MAX_VALUE));

        ChestMenu menu = new ChestMenu("Choose Destination");
        int pages = Math.max(1, (gates.size() + PICKER_PAGE - 1) / PICKER_PAGE);
        int current = Math.min(Math.max(page, 0), pages - 1);

        for (int i = 0; i < PICKER_PAGE; i++) {
            int idx = current * PICKER_PAGE + i;
            if (idx >= gates.size()) break;
            Gate g = gates.get(idx);
            boolean sameWorld = g.location().getWorld().equals(here.getWorld());
            String where = String.format("%s  %d %d %d", g.location().getWorld().getName(), g.location().getBlockX(),
                    g.location().getBlockY(), g.location().getBlockZ());
            menu.addItem(i, new CustomItemStack(g.lit() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                    "&f" + (g.name() != null ? g.name() : "Stargate " + g.address()),
                    "&7Address: " + g.address(), "&7" + where,
                    sameWorld ? "&7Distance: " + (int) Math.sqrt(g.location().distanceSquared(here)) + " blocks" : "&7Other world",
                    g.lit() ? "&aActive" : "&eInactive (activate it to arrive)", "", "&eClick to select"),
                    (pl, slot, item, action) -> {
                        setDestination(g.address(), b, pl);
                        pl.closeInventory();
                        return false;
                    });
        }
        if (gates.isEmpty()) {
            menu.addItem(22, new CustomItemStack(Material.BARRIER, "&cNo other Stargates",
                    "&7Build and place another controller first"), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i = 45; i < 54; i++) {
            menu.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }
        if (current > 0) {
            menu.addItem(45, new CustomItemStack(Material.ARROW, "&fPrevious Page"), (pl, slot, item, action) -> {
                openPicker(b, pl, current - 1);
                return false;
            });
        }
        menu.addItem(49, new CustomItemStack(Material.OAK_DOOR, "&fBack", "&7Page " + (current + 1) + "/" + pages),
                (pl, slot, item, action) -> {
                    getMenu(b, pl).open(pl);
                    return false;
                });
        if (current < pages - 1) {
            menu.addItem(53, new CustomItemStack(Material.ARROW, "&fNext Page"), (pl, slot, item, action) -> {
                openPicker(b, pl, current + 1);
                return false;
            });
        }
        menu.open(p);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onGateBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.END_GATEWAY &&
                Boolean.parseBoolean(BlockStorage.getLocationInfo(b.getLocation(), "locked"))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Deactivate the Stargate before destroying it.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUsePortal(PlayerTeleportEndGatewayEvent e) {
        Location exit = e.getGateway().getExitLocation();
        if (exit == null || !(BlockStorage.check(exit) instanceof StargateController)) return;

        // Siempre se cancela el portal vanilla: antes, sin destino, te dejaba encima del controlador ("el techo").
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (p.hasMetadata("disableStargate")) return;
        p.setMetadata("disableStargate", new FixedMetadataValue(Galactifun.instance(), true));
        Scheduler.run(20, () -> p.removeMetadata("disableStargate", Galactifun.instance()));

        Location dest = BSUtils.getStoredLocation(exit, "dest");
        if (dest == null) {
            p.sendMessage(ChatColor.RED + "This Stargate has no destination. Right-click the controller and pick one.");
            return;
        }

        Block controller = dest.getBlock();
        if (!(BlockStorage.check(controller) instanceof StargateController)) {
            p.sendMessage(ChatColor.RED + "The destination Stargate no longer exists (broken or moved). Pick another destination.");
            return;
        }
        if (getPortalBlocks(controller).isEmpty()) {
            p.sendMessage(ChatColor.RED + "The destination Stargate is off: ask someone to activate it (right-click its controller).");
            return;
        }

        // Llegada: a cualquiera de los dos lados del arco de destino, donde quepa un jugador (1 ancho x 2 alto).
        Orientation o = detectOrientation(controller).orElse(Orientation.EJE_Z);
        int[] n = o.normal();
        Block feet = null;
        int sign = 1;
        StringBuilder bloqueos = new StringBuilder();
        for (int s : new int[] { 1, -1 }) {
            Block f = controller.getRelative(n[0] * s, 0, n[2] * s);
            Block head = f.getRelative(0, 1, 0);
            if (isFree(f) && isFree(head)) {
                feet = f;
                sign = s;
                break;
            }
            Block culprit = !isFree(f) ? f : head;
            if (bloqueos.length() > 0) bloqueos.append("; ");
            bloqueos.append(describeBlock(culprit)).append(" at ").append(culprit.getX()).append(' ')
                    .append(culprit.getY()).append(' ').append(culprit.getZ());
        }

        if (feet == null) {
            p.sendMessage(ChatColor.RED + "Destination blocked: " + bloqueos + ". Keep a spot 1 wide and 2 tall free "
                    + "on either side of the destination controller.");
            return;
        }

        AlienWorld world = Galactifun.worldManager().getAlienWorld(feet.getWorld());
        if (world != null) {
            p.setMetadata("CanTpAlienWorld", new FixedMetadataValue(Galactifun.instance(), true));
        }
        Location target = feet.getLocation().add(0.5, 0, 0.5);
        target.setYaw((float) Math.toDegrees(Math.atan2(-(n[0] * sign), n[2] * sign)));
        target.setPitch(p.getLocation().getPitch());
        p.teleportAsync(target);
    }

    private static boolean isFree(Block b) {
        return b.isPassable() && !b.isLiquid() && BlockStorage.check(b) == null;
    }

}
