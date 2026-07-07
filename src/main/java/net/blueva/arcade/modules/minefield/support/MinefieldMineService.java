package net.blueva.arcade.modules.minefield.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.minefield.state.MinefieldArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MinefieldMineService {

    private final ModuleConfigAPI moduleConfig;
    private final MinefieldMessageService messageService;
    private final MinefieldStatsService statsService;
    private final ConcurrentHashMap<UUID, Long> playerMineImmunity = new ConcurrentHashMap<>();

    public MinefieldMineService(ModuleConfigAPI moduleConfig,
                                MinefieldMessageService messageService,
                                MinefieldStatsService statsService) {
        this.moduleConfig = moduleConfig;
        this.messageService = messageService;
        this.statsService = statsService;
    }

    public void placeMines(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           MinefieldArenaState state) {
        Location floorMin = context.getDataAccess().getGameLocation("game.floor.bounds.min");
        Location floorMax = context.getDataAccess().getGameLocation("game.floor.bounds.max");

        if (floorMin == null || floorMax == null) {
            return;
        }

        if (floorMin.getWorld() == null || floorMax.getWorld() == null) {
            return;
        }

        double spawnChance = getMineSpawnChance();
        clearMineBlocks(state);

        Set<Location> mines = state.getMines();
        mines.clear();

        int minX = Math.min(floorMin.getBlockX(), floorMax.getBlockX());
        int maxX = Math.max(floorMin.getBlockX(), floorMax.getBlockX());
        int minZ = Math.min(floorMin.getBlockZ(), floorMax.getBlockZ());
        int maxZ = Math.max(floorMin.getBlockZ(), floorMax.getBlockZ());

        int plateY = Math.min(floorMin.getBlockY(), floorMax.getBlockY()) + 1;

        List<Material> plates = getPlateMaterials();
        if (plates.isEmpty()) {
            plates = Collections.singletonList(Material.STONE_PRESSURE_PLATE);
        }

        Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
        Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Location plateLocation = new Location(floorMin.getWorld(), x, plateY, z);

                if (finishMin != null && finishMax != null && isInsideBounds(plateLocation, finishMin, finishMax)) {
                    continue;
                }

                if (plateLocation.clone().add(0, -1, 0).getBlock().getType() == Material.AIR) {
                    continue;
                }

                if (ThreadLocalRandom.current().nextDouble() <= spawnChance) {
                    Material plateMaterial = plates.get(ThreadLocalRandom.current().nextInt(plates.size()));
                    plateLocation.getBlock().setType(plateMaterial);
                    mines.add(plateLocation.getBlock().getLocation());
                } else {
                    if (plateLocation.getBlock().getType() != Material.AIR) {
                        plateLocation.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void clearMines(MinefieldArenaState state) {
        clearMineBlocks(state);
        state.setTimerTaskId(null);
    }

    private double getMineSpawnChance() {
        double chance = moduleConfig.getDouble("mines.spawn_chance");
        if (chance > 1) {
            chance = chance / 100.0;
        }

        if (chance <= 0) {
            chance = 0.5;
        }

        return Math.min(1.0, Math.max(0.0, chance));
    }

    private boolean isInsideBounds(Location location, Location min, Location max) {
        return location.getX() >= Math.min(min.getX(), max.getX()) &&
                location.getX() <= Math.max(min.getX(), max.getX()) &&
                location.getY() >= Math.min(min.getY(), max.getY()) &&
                location.getY() <= Math.max(min.getY(), max.getY()) &&
                location.getZ() >= Math.min(min.getZ(), max.getZ()) &&
                location.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    private void clearMineBlocks(MinefieldArenaState state) {
        Set<Location> mines = state.getMines();
        if (mines.isEmpty()) {
            return;
        }

        for (Location mine : mines) {
            Material type = mine.getBlock().getType();
            if (isMineMaterial(type)) {
                mine.getBlock().setType(Material.AIR);
            }
        }

        mines.clear();
    }

    public void handleMineTrigger(Player player,
                                  GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  Location plateLocation) {
        if (!player.isOnline()) {
            return;
        }

        if (hasMineImmunity(player)) {
            return;
        }

        grantMineImmunity(player);

        double verticalForce = moduleConfig.getDouble("mines.explosion.vertical_force", 1.35);
        double backwardForce = moduleConfig.getDouble("mines.explosion.backward_force", 1.05);
        String particleName = moduleConfig.getString("mines.explosion.particle");
        String soundName = moduleConfig.getString("mines.explosion.sound");
        String teleportSpawn = moduleConfig.getString("mines.teleport");

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            plateLocation.getWorld().spawnParticle(particle, plateLocation.clone().add(0.5, 0.2, 0.5), 1);
        } catch (Exception ignored) {
            plateLocation.getWorld().spawnParticle(explosionParticle(), plateLocation.clone().add(0.5, 0.2, 0.5), 1);
        }

        try {
            Sound sound = resolveSound(soundName, Sound.ENTITY_GENERIC_EXPLODE);
            plateLocation.getWorld().playSound(plateLocation, sound, 1.2f, 1.0f);
        } catch (Throwable ignored) {
            plateLocation.getWorld().playSound(plateLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
        }

        if (teleportSpawn == "true") {
            context.respawnPlayer(player);
        } else if (teleportSpawn == "false") {
            Vector direction = player.getLocation().getDirection().normalize();
            Vector velocity = direction.multiply(-backwardForce);
            velocity.setY(verticalForce);
            player.setVelocity(velocity);
        }

        String message = messageService.getMineTriggeredMessage();
        if (message != null) {
            context.getMessagesAPI().sendRaw(player, message);
        }

        statsService.recordMineTriggered(player);
    }

    private boolean hasMineImmunity(Player player) {
        Long until = playerMineImmunity.get(player.getUniqueId());
        if (until == null) {
            return false;
        }

        if (System.currentTimeMillis() > until) {
            playerMineImmunity.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    private void grantMineImmunity(Player player) {
        long duration = moduleConfig.getInt("mines.immunity_millis");
        playerMineImmunity.put(player.getUniqueId(), System.currentTimeMillis() + duration);
    }

    public void clearImmunity(UUID playerId) {
        playerMineImmunity.remove(playerId);
    }

    public void removeMineLocation(MinefieldArenaState state, Location location) {
        Set<Location> mines = state.getMines();
        if (mines.isEmpty()) {
            return;
        }

        mines.removeIf(loc -> loc.getBlockX() == location.getBlockX() &&
                loc.getBlockY() == location.getBlockY() &&
                loc.getBlockZ() == location.getBlockZ());
    }

    public List<Material> getPlateMaterials() {
        List<String> names = moduleConfig.getStringList("mines.plate_materials");
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }

        return names.stream()
                .map(String::toUpperCase)
                .map(name -> {
                    try {
                        return Material.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(material -> material.name().endsWith("PRESSURE_PLATE"))
                .collect(Collectors.toList());
    }

    public boolean isMineMaterial(Material material) {
        List<Material> plates = getPlateMaterials();
        if (plates.isEmpty()) {
            return material == Material.STONE_PRESSURE_PLATE || material == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
        }

        return plates.contains(material);
    }

    public int getActiveMineCount(MinefieldArenaState state) {
        return state.getMines().size();
    }

    public void clearAll() {
        playerMineImmunity.clear();
    }

    private Particle explosionParticle() {
        try {
            return Particle.valueOf("EXPLOSION");
        } catch (IllegalArgumentException ignored) {
            return Particle.EXPLOSION_NORMAL;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Sound resolveSound(String soundName, Sound fallback) {
        if (soundName == null || soundName.isBlank()) {
            return fallback;
        }

        try {
            return (Sound) Enum.valueOf((Class) Sound.class, soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | ClassCastException ignored) {
        }

        try {
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Object key = namespacedKeyClass
                    .getMethod("minecraft", String.class)
                    .invoke(null, toModernSoundKey(soundName));
            Object registry = Class.forName("org.bukkit.Registry").getField("SOUNDS").get(null);
            Object resolved = registry.getClass().getMethod("get", namespacedKeyClass).invoke(registry, key);
            if (resolved instanceof Sound sound) {
                return sound;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return fallback;
    }

    private String toModernSoundKey(String soundName) {
        String normalized = soundName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized.replace('_', '.');
    }
}
