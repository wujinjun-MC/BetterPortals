package com.lauriethefish.betterportals.bukkit.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.command.framework.CommandException;
import com.lauriethefish.betterportals.bukkit.command.framework.CommandTree;
import com.lauriethefish.betterportals.bukkit.command.framework.annotations.*;
import com.lauriethefish.betterportals.bukkit.config.MessageConfig;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.portal.selection.IPortalSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Singleton
public class CustomPortalCommands {
    private static final String[] EASTER_EGG_NAMES = new String[]{"dinnerbone"};
    private static final double MODIFY_DISTANCE = 20.0;

    private final IPortalManager portalManager;
    private final MessageConfig messageConfig;
    private final IPortal.Factory portalFactory;
    private final Provider<IPortalSelection> selectionProvider;

    @Inject
    public CustomPortalCommands(CommandTree commandTree, IPortalManager portalManager, MessageConfig messageConfig, IPortal.Factory portalFactory, Provider<IPortalSelection> selectionProvider) {
        this.portalManager = portalManager;
        this.messageConfig = messageConfig;
        this.portalFactory = portalFactory;
        this.selectionProvider = selectionProvider;

        commandTree.registerCommands(this);
    }

    private @NotNull IPortal getClosestPortal(Player player) throws CommandException {
        IPortal portal = portalManager.findClosestPortal(player.getLocation(), MODIFY_DISTANCE);
        if(portal == null) {
            throw new CommandException(messageConfig.getErrorMessage("noPortalCloseEnough"));
        }

        return portal;
    }

    @Command
    @Path("betterportals/removebyname")
    @Description("Removes all portals with the given name")
    @Argument(name = "portalName")
    @Aliases("deletename")
    public boolean removePortalsByName(CommandSender sender, String portalName) throws CommandException {
        List<IPortal> toRemove = portalManager
                .getAllPortals()
                .stream()
                .filter(portal -> portalName.equals(portal.getName()))
                .collect(Collectors.toList());


        if(toRemove.size() == 0) {
            throw new CommandException(messageConfig.getErrorMessage("noPortalsWithName").replace("{name}", portalName));
        }   else    {
            toRemove.forEach(portalManager::removePortal);
            sender.sendMessage(messageConfig.getChatMessage("portalsRemoved"));
            return true;
        }
    }

    // Oh for the love of god please don't run this command on the behalf of an entity just please don't I beg you why would you even do that?
    private Location normalizeLocalCoordinates(String X, String Y, String Z, String playerName) {
        int coordinateType = 0; // 0: X, 1: Y, 2: Z
        Location playerLocation = Bukkit.getPlayerExact(playerName).getLocation();
        Location normalizedLocation = playerLocation.clone(); // Will store the converted location

        // Iterate through coordinates (X, Y, Z)
        for (String coordinateToConvert : new String[] {X, Y, Z}) {

            // If it is just a ~ or a ^, then give it a zero at the end to fix conversions
            System.out.println("Coordinate Length:");
            System.out.println(coordinateToConvert.replace("~", "").replace("^", "").length());
            if (coordinateToConvert.replace("~", "").replace("^", "").length() == 0) {
                System.out.println("TOO SMALL: ADDING 0");
                coordinateToConvert += '0';
            }

            // Local Coordinate
            if (coordinateToConvert.charAt(0) == '~') {
                // Basically just add the coordinate to the respective player location depending on whether it is X, Y or Z
                switch (coordinateType) {
                    case 0:
                        normalizedLocation.setX( (int) playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("~", "")) );
                        break;
                    case 1:
                        normalizedLocation.setY( (int) playerLocation.getBlockY() + Integer.parseInt(coordinateToConvert.replace("~", "")) );
                        break;
                    case 2:
                        normalizedLocation.setZ( (int) playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("~", "")) );
                        break;
                }

            // Caret Notation Local Coordinate
            } else if (coordinateToConvert.charAt(0) == '^') {
                // Y is handled like standard local coordinate
                if (coordinateType == 1) {
                    normalizedLocation.setY( playerLocation.getBlockY() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                }

                // Get the direction the player is facing
                float playerYaw = Location.normalizeYaw(playerLocation.getYaw());

                /**
                 * This code basically:
                 * - Checks the direction the player is facing
                 * - Adds to the corresponding coordinate depending on that
                 * 
                 * Note that Y is ommited as it is handled above
                 */
                if (playerYaw >= -45 && playerYaw < 45) {
                    // Facing +z
                    switch (coordinateType) {
                        case 0:
                            normalizedLocation.setX( playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                        case 2:
                            normalizedLocation.setZ( playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                    }
                } else if (playerYaw >= 45 && playerYaw < 135) {
                    // Facing -x
                    switch (coordinateType) {
                        case 0:
                            normalizedLocation.setZ( playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                        case 2:
                            normalizedLocation.setX( playerLocation.getBlockX() - Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                    }
                } else if ((playerYaw >= 135 && playerYaw <= 180) || (playerYaw >= -180 && playerYaw < -135)) {
                    // Facing -z
                    switch (coordinateType) {
                        case 0:
                            normalizedLocation.setX( playerLocation.getBlockX() - Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                        case 2:
                            normalizedLocation.setZ( playerLocation.getBlockZ() - Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                    }
                } else if (playerYaw >= -135 && playerYaw < -45) {
                    // Facing +x
                    switch (coordinateType) {
                        case 0:
                            normalizedLocation.setZ( playerLocation.getBlockZ() - Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                        case 2:
                            normalizedLocation.setX( playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                            break;
                    }
                }

            // Absolute coordinates
            } else {
                // Set the location to the coordinate
                switch (coordinateType) {
                    case 0:
                        normalizedLocation.setX(Integer.parseInt(coordinateToConvert));
                        break;
                    case 1:
                        normalizedLocation.setY(Integer.parseInt(coordinateToConvert));
                        break;
                    case 2:
                        normalizedLocation.setZ(Integer.parseInt(coordinateToConvert));
                        break;
                }
            }

            // Increment the coordinate type in the order: X, Y, Z
            coordinateType++;
        }

        System.out.println("=====");
        System.out.println(playerLocation.getYaw());
        System.out.println(normalizedLocation.getBlockX());
        System.out.println(normalizedLocation.getBlockY());
        System.out.println(normalizedLocation.getBlockZ());
        System.out.println("=====");

        // Return the new location
        return normalizedLocation;
    }

    @Command
    @Path("betterportals/createfromcoords")
    @Description("Creates a portal from the coordinates of its corners without requiring a player")
    @Argument(name = "originWorld")
    @Argument(name = "originX1")
    @Argument(name = "originY1")
    @Argument(name = "originZ1")
    @Argument(name = "originX2")
    @Argument(name = "originY2")
    @Argument(name = "originZ2")
    @Argument(name = "destWorld")
    @Argument(name = "destX1")
    @Argument(name = "destY1")
    @Argument(name = "destZ1")
    @Argument(name = "destX2")
    @Argument(name = "destY2")
    @Argument(name = "destZ2")
    @Argument(name = "twoWay?", defaultValue = "false")
    @Argument(name = "invert?", defaultValue = "false")
    @Argument(name = "name", defaultValue = " no name")
    public boolean createFromCoordinates(CommandSender sender,
                                         String originWorld,
                                         String originX1, String originY1, String originZ1,
                                         String originX2, String originY2, String originZ2,
                                         String destWorld,
                                         String destX1, String destY1, String destZ1,
                                         String destX2, String destY2, String destZ2,
                                         String twoWayStr,
                                         String invertStr,
                                         String name
    ) throws CommandException {
        if(" no name".equals(name)) {
            name = null;
        }

        // Normalize coordinates
        Location origin1 = normalizeLocalCoordinates(originX1, originY1, originZ1, sender.getName());
        Location origin2 = normalizeLocalCoordinates(originX2, originY2, originZ2, sender.getName());
        Location dest1 = normalizeLocalCoordinates(destX1, destY1, destZ1, sender.getName());
        Location dest2 = normalizeLocalCoordinates(destX2, destY2, destZ2, sender.getName());


        boolean twoWay = twoWayStr.equalsIgnoreCase("true") || twoWayStr.equalsIgnoreCase("twoWay") || twoWayStr.equalsIgnoreCase("dual");
        boolean invert = invertStr.equalsIgnoreCase("true") || invertStr.equalsIgnoreCase("invert");

        IPortalSelection origin = makeSelection(originWorld, origin1.getBlockX(), origin1.getBlockY(), origin1.getBlockZ(), origin2.getBlockX(), origin2.getBlockY(), origin2.getBlockZ());
        IPortalSelection dest = makeSelection(destWorld, dest1.getBlockX(), dest1.getBlockY(), dest1.getBlockZ(), dest2.getBlockX(), dest2.getBlockY(), dest2.getBlockZ());

        if(!origin.getPortalSize().equals(dest.getPortalSize())) {
            throw new CommandException(messageConfig.getErrorMessage("differentSizes"));
        }

        if(invert) {
            dest.invertDirection();
        }

        IPortal portal = portalFactory.create(origin.getPortalPosition(), dest.getPortalPosition(),
                origin.getPortalSize(), true, UUID.randomUUID(), null, name, true);
        portalManager.registerPortal(portal);

        // Add another portal going back if we're in two way mode
        if(twoWay) {
            IPortal reversePortal = portalFactory.create(dest.getPortalPosition(), origin.getPortalPosition(),
                    origin.getPortalSize(), true, UUID.randomUUID(), null, name, true);
            portalManager.registerPortal(reversePortal);
        }
        sender.sendMessage(messageConfig.getChatMessage("portalCreated"));

        return true;
    }

    private IPortalSelection makeSelection(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) throws CommandException {
        World world = Bukkit.getWorld(worldName);
        if(world == null) {
            throw new CommandException(messageConfig.getErrorMessage("noWorldExistsWithGivenName").replace("{name}", worldName));
        }

        IPortalSelection selection = selectionProvider.get();
        selection.setPositionA(new Location(world, x1, y1, z1));
        selection.setPositionB(new Location(world, x2, y2, z2));

        if(!selection.isValid()) {
            throw new CommandException(String.format(messageConfig.getErrorMessage("coordinatesNotInLine"),
                    x1, y1, z1, x2, y2, z2));
        }

        return selection;
    }
    @Command
    @Path("betterportals/remove")
    @RequiresPermissions("betterportals.remove")
    @RequiresPlayer
    @Aliases({"delete", "del"})
    @Description("Removes the nearest portal within 20 blocks of the player")
    @Argument(name = "removeDestination?", defaultValue = "true")
    public boolean deleteNearest(Player player, boolean removeDestination) throws CommandException {
        IPortal portal = getClosestPortal(player);

        // If the player doesn't own the portal, and doesn't have permission to remove portals that aren't theirs, don't remove
        if(!player.hasPermission("betterportals.remove.others") && !player.getUniqueId().equals(portal.getOwnerId())) {
            throw new CommandException(messageConfig.getErrorMessage("removeNotOwnedByPlayer"));
        }

        portalManager.removePortal(portal);
        // We can't remove the destination on cross-server portals
        if(removeDestination && !portal.isCrossServer()) {
            Location destPosition = portal.getDestPos().getLocation();
            portalManager.removePortalsAt(destPosition);
        }

        player.sendMessage(messageConfig.getChatMessage("portalRemoved"));
        return true;
    }

    @Command
    @Path("betterportals/setOrigin")
    @Aliases("origin")
    @RequiresPermissions("betterportals.select")
    @RequiresPlayer
    @Description("Sets the current portal wand selection as your origin position")
    public boolean setOrigin(IPlayerData playerData) throws CommandException    {
        playerData.getSelection().trySelectOrigin();
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("originPortalSet"));
        return true;
    }

    @Command
    @Path("betterportals/setDestination")
    @Aliases({"destination", "dest"})
    @RequiresPermissions("betterportals.select")
    @RequiresPlayer
    @Description("Sets the current portal wand selection as your destination position")
    public boolean setDestination(IPlayerData playerData) throws CommandException    {
        playerData.getSelection().trySelectDestination();
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("destPortalSet"));
        return true;
    }

    @Command
    @Path("betterportals/linkPortals")
    @Aliases("link")
    @RequiresPermissions("betterportals.link")
    @RequiresPlayer
    @Description("Links the origin and destination portal together")
    @Argument(name = "twoWay?", defaultValue = "false")
    @Argument(name = "invert?", defaultValue = "false")
    public boolean linkPortals(IPlayerData playerData, String twoWayStr, String invertStr) throws CommandException  {
        boolean twoWay = twoWayStr.equalsIgnoreCase("true") || twoWayStr.equalsIgnoreCase("twoWay") || twoWayStr.equalsIgnoreCase("dual");
        boolean invert = invertStr.equalsIgnoreCase("true") || invertStr.equalsIgnoreCase("invert");

        playerData.getSelection().tryCreateFromSelection(playerData.getPlayer(), twoWay, invert);
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("portalsLinked"));
        return true;
    }

    @Command
    @Path("betterportals/linkExternalPortals")
    @Aliases("linkexternal")
    @RequiresPermissions("betterportals.linkexternal")
    @RequiresPlayer
    @Description("Links the origin selection on this server with a destination on another server")
    @Argument(name = "invert?", defaultValue = "false")
    public boolean linkExternalPortals(IPlayerData playerData, boolean invert) throws CommandException  {
        playerData.getSelection().tryCreateFromExternalSelection(playerData.getPlayer(), invert);
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("portalsLinked"));
        return true;
    }

    @Command
    @Path("betterportals/wand")
    @RequiresPermissions("betterportals.wand")
    @RequiresPlayer
    @Description("Gives you the wand for selecting portals")
    public boolean getPortalWand(Player player) {
        player.getInventory().addItem(messageConfig.getPortalWand());
        return true;
    }

    // Some of the easter eggs require a portal recreation to work properly, this handles that
    private void setName(IPortal portal, String name) {
        boolean isEgg = false;
        for(String egg : EASTER_EGG_NAMES) {
            if (egg.equalsIgnoreCase(name) || egg.equalsIgnoreCase(portal.getName())) {
                isEgg = true;
                break;
            }
        }
        // Non-easter-egg portals can just get their name set normally
        if(!isEgg) {
            portal.setName(name);
            return;
        }

        portalManager.removePortal(portal);
        IPortal replacement = portalFactory.create(
                portal.getOriginPos(),
                portal.getDestPos(),
                portal.getSize(),
                portal.isCustom(),
                portal.getId(),
                portal.getOwnerId(),
                name,
                true
        );

        portalManager.registerPortal(replacement);
    }

    @Command
    @Path("betterportals/setPortalName")
    @RequiresPermissions("betterportals.setname")
    @Argument(name = "newName")
    @Aliases("setname")
    @RequiresPlayer
    @Description("Sets the name of the nearest portal within 20 blocks")
    public boolean setName(Player player, String newName) throws CommandException   {
        IPortal portal = getClosestPortal(player);

        // If the player doesn't own the portal, and doesn't have permission to remove portals that aren't theirs, don't remove
        if(!player.hasPermission("betterportals.setname.others") && !player.getUniqueId().equals(portal.getOwnerId())) {
            throw new CommandException(messageConfig.getErrorMessage("nameNotOwnedbyPlayer"));
        }

        // Nether portals cannot be named!
        if(portal.isNetherPortal()) {
            throw new CommandException(messageConfig.getErrorMessage("nameNetherPortal"));
        }

        setName(portal, newName);
        player.sendMessage(messageConfig.getChatMessage("changedName"));
        return true;
    }

    @Command
    @Path("betterportals/getportalname")
    @RequiresPermissions("betterportals.getname")
    @RequiresPlayer
    @Aliases("getname")
    @Description("Tells you the name of the nearest portal within 20 blocks")
    public boolean getName(Player player) throws CommandException {
        IPortal portal = getClosestPortal(player);

        String name = portal.getName();
        if(name == null) {
            throw new CommandException(messageConfig.getErrorMessage("noName"));
        }

        String nameFormat = messageConfig.getChatMessage("currentName");
        nameFormat = nameFormat.replace("{name}", portal.getName());
        player.sendMessage(nameFormat);
        return true;
    }

    @Command
    @Path("betterportals/getallowNonPlayerTeleportation")
    @RequiresPermissions("betterportals.getallowNonPlayerTeleportation")
    @RequiresPlayer
    @Aliases("getcanteleportmobs")
    @Description("Tells you whether or not the nearest portal within 20 blocks allows item teleportation")
    public boolean getAllowNonPlayerTeleportation(Player player) throws CommandException {
        IPortal portal = getClosestPortal(player);

        if(portal.allowsNonPlayerTeleportation()) {
            player.sendMessage(messageConfig.getChatMessage("allowsItems"));
        }   else    {
            player.sendMessage(messageConfig.getChatMessage("doesNotAllowItems"));
        }

        return true;
    }

    @Command
    @Path("betterportals/setAllowNonPlayerTeleportation")
    @RequiresPermissions("betterportals.setAllowNonPlayerTeleportation")
    @RequiresPlayer
    @Aliases("setcanteleportmobs")
    @Description("Sets whether or not the nearest portal within 20 blocks allows item teleportation")
    @Argument(name = "allow")
    public boolean setAllowNonPlayerTeleportation(Player player, boolean allowTeleportation) throws CommandException {
        IPortal portal = getClosestPortal(player);

        portal.setAllowsNonPlayerTeleportation(allowTeleportation);
        if(allowTeleportation) {
            player.sendMessage(messageConfig.getChatMessage("changedAllowsItems"));
        }   else    {
            player.sendMessage(messageConfig.getChatMessage("changedDoesNotAllowItems"));
        }

        return true;
    }

    @Command
    @Path("betterportals/setseethroughportal")
    @RequiresPermissions("betterportals.see")
    @RequiresPlayer
    @Aliases("setenablebpview")
    @Description("Sets whether or not the current player is able to see what's on the other side of a portal.")
    @Argument(name = "seethroughportal")
    public boolean setSeeThroughPortal(IPlayerData playerData, boolean seeThroughPortal) {
        Player player = playerData.getPlayer();
        if (seeThroughPortal) {
            playerData.getPermanentData().set("seeThroughPortal", true);
            playerData.savePermanentData();
            player.sendMessage(messageConfig.getChatMessage("seeThroughPortalEnabled"));
        }

        else {
            playerData.getPermanentData().set("seeThroughPortal", false);
            playerData.savePermanentData();
            player.sendMessage(messageConfig.getChatMessage("seeThroughPortalDisabled"));
        }

        return true;
    }

    @Command
    @Path("betterportals/toggleseethroughportal")
    @RequiresPermissions("BetterPortals.see")
    @RequiresPlayer
    @Aliases("togglevanillaview")
    @Description("Toggles whether or not the current player is able to see what's on the other side of a portal.")
    public boolean toggleSeeThroughPortal(IPlayerData playerData) {
        setSeeThroughPortal(playerData, !playerData.getPermanentData().getBoolean("seeThroughPortal"));

        return true;
    }


}
