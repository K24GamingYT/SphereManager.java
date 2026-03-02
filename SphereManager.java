package me.kartik.sphereSmp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class SphereManager implements Listener, CommandExecutor, TabCompleter {

    private final SphereSmp plugin;
    private final File      playerDataFolder;

    // ── Core ability tracking ──────────────────────────────────────────────
    private final Map<String, Integer>    activeMap      = new HashMap<>();
    private final Map<String, Integer>    cooldownMap    = new HashMap<>();
    private final Map<String, BukkitTask> durationTasks  = new HashMap<>();
    private final Map<String, BukkitTask> cooldownTasks  = new HashMap<>();
    private final Map<UUID,   BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID,   BukkitTask> passiveTasks   = new HashMap<>();
    private final Map<String, Integer>    hitCounters    = new HashMap<>();

    // ── General sphere state ───────────────────────────────────────────────
    private final Map<UUID, List<Location>> iceTrailBlocks      = new HashMap<>();
    private final Set<UUID>                 frostSnowballActive = new HashSet<>();
    private final Map<UUID, BukkitTask>     shadowZoneTasks     = new HashMap<>();
    private final Map<UUID, Integer>        windCharges         = new HashMap<>();
    private final Map<UUID, BukkitTask>     windChargeTasks     = new HashMap<>();
    private final Map<UUID, BukkitTask>     fireBeamTasks       = new HashMap<>();
    private final Set<UUID>                 earthDiscountActive = new HashSet<>();
    private final Set<UUID>                 shadowArmorHidden   = new HashSet<>();

    // ── Fire / Wind ramp ──────────────────────────────────────────────────
    private final Map<UUID, Integer>    windSpeedLevel = new HashMap<>();
    private final Map<UUID, BukkitTask> windSpeedTasks = new HashMap<>();
    private final Map<UUID, Integer>    fireLavaLevel  = new HashMap<>();
    private final Map<UUID, BukkitTask> fireLavaTasks  = new HashMap<>();

    // ── Frost prison ──────────────────────────────────────────────────────
    private final Map<Location, UUID>       icePrisonMap      = new HashMap<>();
    private final Map<UUID, List<Location>> icePrisonBlocks   = new HashMap<>();
    private final Map<UUID, BukkitTask>     icePrisonDmgTasks = new HashMap<>();

    // ── Volt ──────────────────────────────────────────────────────────────
    private final Set<UUID>             voltLightningImmune = new HashSet<>();
    private final Set<UUID>             acidRainActive      = new HashSet<>();
    private final Map<UUID, BukkitTask> acidRainTasks       = new HashMap<>();
    private final Map<UUID, BukkitTask> thunderApocTasks    = new HashMap<>();

    // ── WARDEN ────────────────────────────────────────────────────────────
    private final Map<UUID, BukkitTask>              wardenDomainTasks      = new HashMap<>();
    private final Map<UUID, List<Location>>          wardenDomainBlocks     = new HashMap<>();
    private final Map<UUID, Map<Location, Material>> wardenDomainOrigBlocks = new HashMap<>();
    private final Map<UUID, Warden>                  wardenMorphEntities    = new HashMap<>();
    private final Map<UUID, BukkitTask>              wardenMorphFollowTasks = new HashMap<>();
    private final Map<UUID, BukkitTask>              wardenTremorTasks      = new HashMap<>();
    private final Map<UUID, Double>                  wardenOrigMaxHp        = new HashMap<>();
    private final Set<UUID>                          wardenDomainActive     = new HashSet<>();
    private final Set<UUID>                          wardenMorphActive      = new HashSet<>();
    private final Map<UUID, ArmorStand>              wardenMorphNameTags    = new HashMap<>();
    private final Map<UUID, BukkitTask>              wardenMorphTagTasks    = new HashMap<>();
    private final Set<UUID>                          morphBoomUsed          = new HashSet<>();

    // ── EARTH ─────────────────────────────────────────────────────────────
    private final Set<UUID> earthBlessingActive  = new HashSet<>();
    private final Set<UUID> earthGoldenAppleUsed = new HashSet<>();

    // ── WATER (NEW) ───────────────────────────────────────────────────────
    private final Set<UUID>             waterBeamUsed   = new HashSet<>();
    private final Map<UUID, BukkitTask> waterPullTasks  = new HashMap<>();
    private final Map<UUID, BukkitTask> drowningEffects = new HashMap<>(); // keyed by VICTIM UUID

    // ── NamespacedKeys ────────────────────────────────────────────────────
    private static final NamespacedKey FIRE_LAVA_KEY =
            new NamespacedKey("spheresmp", "fire_lava_dmg");
    private static final NamespacedKey WIND_SPEED_KEY =
            new NamespacedKey("spheresmp", "wind_speed_ramp");
    private static final NamespacedKey EARTH_ENCHANT_KEY =
            new NamespacedKey("spheresmp", "earth_enchanted");
    private static final NamespacedKey EARTH_BLESS_HEALTH_KEY =
            new NamespacedKey("spheresmp", "earthblessinghealth");
    private static final NamespacedKey FROST_BOMB_KEY =
            new NamespacedKey("spheresmp", "frost_bomb");
    private static final NamespacedKey WATER_DEPTH_STRIDER_KEY =
            new NamespacedKey("spheresmp", "water_depth_strider_lvl");

    // ── Earth static sets/maps ────────────────────────────────────────────
    private static final Set<Material> EARTH_TOOL_TYPES = Set.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE);

    private static final Set<Material> ORE_TYPES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS, Material.NETHER_QUARTZ_ORE);

    private static final Map<Material, Material> SMELT_MAP = Map.ofEntries(
            Map.entry(Material.IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET),
            Map.entry(Material.COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT));

    private static final List<String> SPHERE_IDS = List.of(
            "fire", "wind", "volt", "water", "earth", "warden", "shadow", "frost");

    public SphereManager(SphereSmp plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) playerDataFolder.mkdirs();
    }

    // ── Utility ───────────────────────────────────────────────────────────

    public static Component leg(String s) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
    public List<String> getSphereIds() { return SPHERE_IDS; }
    public String getSphereType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(plugin.KEY_SPHERE_TYPE, PersistentDataType.STRING, null);
    }
    public boolean isSphere(ItemStack item) { return getSphereType(item) != null; }
    private int cfgI(String t, int a, String k) {
        return plugin.getConfig().getInt("spheres."+t+".ability"+a+"."+k, 30);
    }
    private double cfgD(String t, int a, String k) {
        return plugin.getConfig().getDouble("spheres."+t+".ability"+a+"."+k, 1.0);
    }
    private String abilityKey(UUID u, String t, int n) { return u+":"+t+":"+n; }
    private void cancelTaskByUUID(Map<UUID, BukkitTask> m, UUID u) {
        BukkitTask t = m.remove(u); if (t != null && !t.isCancelled()) t.cancel();
    }
    private void cancelTaskByKey(Map<String, BukkitTask> m, String k) {
        BukkitTask t = m.remove(k); if (t != null && !t.isCancelled()) t.cancel();
    }
    private int incrementHit(Player p, String t) {
        String k = p.getUniqueId()+":"+t;
        int v = hitCounters.getOrDefault(k, 0)+1; hitCounters.put(k, v); return v;
    }
    private List<Player> nearbyPlayers(Player p, double r) {
        List<Player> l = new ArrayList<>();
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), r, r, r))
            if (e instanceof Player t && !t.equals(p)) l.add(t);
        return l;
    }
    private void msg(Player p, String k) {
        p.sendMessage(leg(plugin.getConfig().getString("messages."+k, "&7Error.")));
    }
    private boolean isExposedToSky(Player p) {
        Location l = p.getLocation();
        return l.getWorld().getHighestBlockYAt(l) <= l.getBlockY()+1;
    }
    private boolean isLockedSphere(Player p, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !isSphere(item)) return false;
        String t = getSphereType(item); if (t == null) return false;
        UUID u = p.getUniqueId();
        return activeMap.containsKey(abilityKey(u,t,1))||activeMap.containsKey(abilityKey(u,t,2))
                ||cooldownMap.containsKey(abilityKey(u,t,1))||cooldownMap.containsKey(abilityKey(u,t,2));
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private void savePlayerCooldowns(Player p) {
        UUID u = p.getUniqueId(); String us = u.toString();
        YamlConfiguration c = new YamlConfiguration(); boolean has = false;
        for (var e : cooldownMap.entrySet()) {
            if (!e.getKey().startsWith(us)) continue;
            String[] sp = e.getKey().replace(us+":","").split(":");
            if (sp.length!=2) continue; String sk = sp[0]+"_"+sp[1];
            if (!c.contains(sk)) { c.set(sk, e.getValue()); has = true; }
        }
        for (var e : activeMap.entrySet()) {
            if (!e.getKey().startsWith(us)) continue;
            String[] sp = e.getKey().replace(us+":","").split(":");
            if (sp.length!=2) continue; String sk = sp[0]+"_"+sp[1];
            if (!c.contains(sk)) { try { int cd = cfgI(sp[0],Integer.parseInt(sp[1]),"cooldown");
                if (cd>0) { c.set(sk,cd); has=true; } } catch (Exception ignored) {} }
        }
        if (has) { File f = new File(playerDataFolder, u+".yml");
            try { c.save(f); } catch (Exception ex) { plugin.getLogger().warning("Save fail "+u+": "+ex.getMessage()); } }
    }
    private void loadPlayerCooldowns(Player p) {
        UUID u = p.getUniqueId(); File f = new File(playerDataFolder, u+".yml");
        if (!f.exists()) return; YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        for (String k : c.getKeys(false)) { int lu = k.lastIndexOf('_'); if (lu<0) continue;
            String t = k.substring(0,lu), ns = k.substring(lu+1);
            if (!SPHERE_IDS.contains(t)) continue; int rem = c.getInt(k); if (rem<=0) continue;
            try { startCooldownFromRemaining(p,t,Integer.parseInt(ns),rem); } catch (NumberFormatException ignored) {} }
        f.delete();
    }
    private void startCooldownFromRemaining(Player p, String t, int n, int s) {
        if (s<=0||!SPHERE_IDS.contains(t)) return; String k = abilityKey(p.getUniqueId(),t,n);
        cooldownMap.put(k,s);
        BukkitTask ct = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int l = s; @Override public void run() { l--;
                if (l<=0) { cooldownMap.remove(k); cancelTaskByKey(cooldownTasks,k);
                    if (p.isOnline()) { ItemStack o = p.getInventory().getItemInOffHand();
                        if (isSphere(o)&&t.equals(getSphereType(o))) refreshActionBar(p,t); }
                } else cooldownMap.put(k,l); }
        }, 20L, 20L);
        cancelTaskByKey(cooldownTasks,k); cooldownTasks.put(k,ct);
    }

    // ── Sphere Item Builder ───────────────────────────────────────────────

    public ItemStack buildSphere(String type) {
        ItemStack item = new ItemStack(Material.PAPER); ItemMeta meta = item.getItemMeta();
        meta.displayName(leg(plugin.getConfig().getString("spheres."+type+".display-name","&f"+type)));
        meta.setCustomModelData(plugin.getConfig().getInt("spheres."+type+".custom-model-data",1000));
        meta.setUnbreakable(true); meta.lore(buildLore(type));
        meta.getPersistentDataContainer().set(plugin.KEY_SPHERE_TYPE, PersistentDataType.STRING, type);
        item.setItemMeta(meta); return item;
    }

    // ── Frost Bomb Snowball ───────────────────────────────────────────────

    private ItemStack buildFrostBombSnowball() {
        ItemStack i = new ItemStack(Material.SNOWBALL,1); ItemMeta m = i.getItemMeta();
        m.displayName(leg("&f&l❄ Frost Bomb"));
        m.lore(List.of(leg(""),leg("&7Throw to create an &ficeberg"),leg("&7Unlimited while ability is active"),leg("")));
        m.getPersistentDataContainer().set(FROST_BOMB_KEY, PersistentDataType.BYTE,(byte)1);
        i.setItemMeta(m); return i;
    }
    private boolean isFrostBombSnowball(ItemStack i) {
        if (i==null||i.getType()!=Material.SNOWBALL||!i.hasItemMeta()) return false;
        return i.getItemMeta().getPersistentDataContainer().has(FROST_BOMB_KEY, PersistentDataType.BYTE);
    }
    private void removeFrostBombSnowballs(Player p) {
        for (int i=0;i<p.getInventory().getSize();i++)
            if (isFrostBombSnowball(p.getInventory().getItem(i))) p.getInventory().setItem(i,null);
        if (isFrostBombSnowball(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(null);
    }

    // ── Earth Helpers ─────────────────────────────────────────────────────

    private void applyEarthEnchants(ItemStack i) {
        if (i==null||i.getType().isAir()||!EARTH_TOOL_TYPES.contains(i.getType())) return;
        ItemMeta m = i.getItemMeta(); if (m==null) return; boolean c = false;
        if (!m.hasEnchant(Enchantment.FORTUNE)) { m.addEnchant(Enchantment.FORTUNE,3,true); c=true; }
        if (!m.hasEnchant(Enchantment.UNBREAKING)) { m.addEnchant(Enchantment.UNBREAKING,3,true); c=true; }
        if (c) { m.getPersistentDataContainer().set(EARTH_ENCHANT_KEY,PersistentDataType.BYTE,(byte)1); i.setItemMeta(m); }
    }
    private void removeEarthEnchants(ItemStack i) {
        if (i==null||i.getType().isAir()) return; ItemMeta m = i.getItemMeta(); if (m==null) return;
        if (m.getPersistentDataContainer().has(EARTH_ENCHANT_KEY,PersistentDataType.BYTE)) {
            m.removeEnchant(Enchantment.FORTUNE); m.removeEnchant(Enchantment.UNBREAKING);
            m.getPersistentDataContainer().remove(EARTH_ENCHANT_KEY); i.setItemMeta(m);
        }
    }
    private void veinMine(Player p, Block origin, boolean autoSmelt) {
        Material type = origin.getType(); Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>(); queue.add(origin);
        while (!queue.isEmpty()&&visited.size()<64) { Block b = queue.poll();
            if (visited.contains(b)||b.getType()!=type) continue; visited.add(b);
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue; Block nb = b.getRelative(dx,dy,dz);
                if (!visited.contains(nb)&&nb.getType()==type) queue.add(nb); } }
        for (Block b : visited) { if (b.equals(origin)) continue;
            if (autoSmelt&&SMELT_MAP.containsKey(b.getType())) {
                Material sm = SMELT_MAP.get(b.getType()); Location l = b.getLocation();
                b.setType(Material.AIR); l.getWorld().dropItemNaturally(l,new ItemStack(sm));
            } else b.breakNaturally(p.getInventory().getItemInMainHand()); }
    }

    // ── Water Helpers (NEW) ───────────────────────────────────────────────

    private void removeDepthStrider(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        if (boots==null||boots.getType().isAir()) return;
        ItemMeta m = boots.getItemMeta(); if (m==null||!m.hasEnchant(Enchantment.DEPTH_STRIDER)) return;
        int lvl = m.getEnchantLevel(Enchantment.DEPTH_STRIDER);
        m.getPersistentDataContainer().set(WATER_DEPTH_STRIDER_KEY, PersistentDataType.INTEGER, lvl);
        m.removeEnchant(Enchantment.DEPTH_STRIDER); boots.setItemMeta(m);
    }
    private void restoreDepthStrider(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        if (boots==null||boots.getType().isAir()) return;
        ItemMeta m = boots.getItemMeta(); if (m==null) return;
        if (m.getPersistentDataContainer().has(WATER_DEPTH_STRIDER_KEY, PersistentDataType.INTEGER)) {
            int lvl = m.getPersistentDataContainer().get(WATER_DEPTH_STRIDER_KEY, PersistentDataType.INTEGER);
            m.addEnchant(Enchantment.DEPTH_STRIDER, lvl, true);
            m.getPersistentDataContainer().remove(WATER_DEPTH_STRIDER_KEY); boots.setItemMeta(m);
        }
    }
    private void applyDrowning(LivingEntity target, int durationSeconds) {
        UUID vid = target.getUniqueId();
        BukkitTask old = drowningEffects.remove(vid);
        if (old!=null&&!old.isCancelled()) old.cancel();
        if (target instanceof Player tp) tp.setRemainingAir(0);
        target.getWorld().spawnParticle(Particle.BUBBLE_POP, target.getLocation().add(0,1.5,0), 20, 0.4, 0.4, 0.4, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_DROWNED_AMBIENT, 1f, 1.2f);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0; final int maxTicks = durationSeconds * 20;
            @Override public void run() {
                if (!target.isValid()||target.isDead()||ticks>=maxTicks) {
                    BukkitTask t = drowningEffects.remove(vid);
                    if (t!=null&&!t.isCancelled()) t.cancel();
                    if (target instanceof Player tp && tp.isOnline()) tp.setRemainingAir(tp.getMaximumAir());
                    return;
                }
                if (target instanceof Player tp) tp.setRemainingAir(0);
                if (ticks%20==0) { target.damage(2.0);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_DROWNED_HURT, 0.6f, 1.0f); }
                if (ticks%5==0) target.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        target.getLocation().add(0,1.5,0), 5, 0.3,0.3,0.3, 0.03);
                ticks++;
            }
        }, 0L, 1L);
        drowningEffects.put(vid, task);
    }
    private void fireWaterTridentBeam(Player p) {
        UUID uuid = p.getUniqueId(); waterBeamUsed.add(uuid);
        World world = p.getWorld();
        Location origin = p.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        Trident trident = world.spawn(origin.clone().add(dir.clone().multiply(1.5)), Trident.class, t -> {
            t.setShooter(p); t.setVelocity(dir.clone().multiply(3.0));
            t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); t.setDamage(0);
        });
        trident.setMetadata("waterBeam", new FixedMetadataValue(plugin, uuid.toString()));
        // Particle trail
        BukkitTask trail = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (trident.isDead()||!trident.isValid()) return;
            Location loc = trident.getLocation();
            world.spawnParticle(Particle.DRIPPING_WATER, loc, 12, 0.4,0.4,0.4, 0.01);
            world.spawnParticle(Particle.BUBBLE_POP, loc, 6, 0.3,0.3,0.3, 0.02);
            world.spawnParticle(Particle.SPLASH, loc, 4, 0.2,0.2,0.2, 0.05);
        }, 0L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!trail.isCancelled()) trail.cancel();
            if (!trident.isDead()) trident.remove();
        }, 100L);
        world.playSound(origin, Sound.ITEM_TRIDENT_THROW, 2f, 0.8f);
        world.playSound(origin, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 1.2f);
        p.sendMessage(leg("&9&lWater Beam &7— Trident fired!"));
        p.sendActionBar(leg("&9Water Beam &7— Used for this activation!"));
    }

    // ── Lore Builder ──────────────────────────────────────────────────────

    private List<Component> buildLore(String type) {
        List<Component> lore = new ArrayList<>(); lore.add(leg(""));
        switch (type) {
            case "fire" -> {
                lore.add(leg("&c&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :"));
                lore.add(leg("&c🔥 &8Fire Resistance — immune to fire & lava"));
                lore.add(leg("&c🔥 &8Stand in lava → ramps up to Strength III"));
                lore.add(leg("&c🔥 &840% chance to ignite enemies on hit"));
                lore.add(leg(""));
                lore.add(leg("&c&lᴀʙɪʟɪᴛʏ ɪ &8— &cMeteor Strike"));
                lore.add(leg("&c🔥 &8Crater erupts — launches all nearby enemies"));
                lore.add(leg("&c🔥 &8Ground turns magma — 5 hearts damage"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &c" + cfgI(type,1,"cooldown") + "s"));
                lore.add(leg(""));
                lore.add(leg("&c&lᴀʙɪʟɪᴛʏ ɪɪ &8— &c&lInfernal Flamethrower"));
                lore.add(leg("&c🔥 &8Thick continuous fire stream from your core"));
                lore.add(leg("   &8Ignites everything in its path"));
                lore.add(leg("   &8Knocks back enemies — sets ground ablaze"));
                lore.add(leg("   &8Layered: flame, soul fire, smoke, lava drip"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &c" + cfgI(type,2,"duration") + "s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &c" + cfgI(type,2,"cooldown") + "s"));
            }
            case "wind" -> {
                lore.add(leg("&b&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&b🌀 &8Speed I always active"));
                lore.add(leg("&b🌀 &8Every 25s speed ramps: I → II → III → IV → V"));
                lore.add(leg("&b🌀 &8Taking damage resets speed to I")); lore.add(leg(""));
                lore.add(leg("&b&lᴀʙɪʟɪᴛʏ ɪ &8— &bWind Dash"));
                lore.add(leg("&b🌀 &8Launches you upward and forward at high speed"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &b" + cfgI(type,1,"cooldown") + "s")); lore.add(leg(""));
                lore.add(leg("&b&lᴀʙɪʟɪᴛʏ ɪɪ &8— &bRavager Roar"));
                lore.add(leg("&b🌀 &8Launches all enemies into air — slams to ground"));
                lore.add(leg("&b🌀 &8Grants 15 wind charges — jump to boost"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &b" + cfgI(type,2,"cooldown") + "s"));
            }
            case "volt" -> {
                lore.add(leg("&e&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&e⚡ &8Speed I always active"));
                lore.add(leg("&e⚡ &8Every 10th hit smites enemy (5th in rain)")); lore.add(leg(""));
                lore.add(leg("&e&lᴀʙɪʟɪᴛʏ ɪ &8— &eThunder Apocalypse"));
                lore.add(leg("&e⚡ &8Dense lightning grid storms the entire area"));
                lore.add(leg("&e⚡ &8Stuns — weather clears when ability ends"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &e"+cfgI(type,1,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &e"+cfgI(type,1,"cooldown")+"s")); lore.add(leg(""));
                lore.add(leg("&e&lᴀʙɪʟɪᴛʏ ɪɪ &8— &eAcid Rain"));
                lore.add(leg("&e⚡ &8Acid rain — ½ heart/s to all exposed players"));
                lore.add(leg("&e⚡ &8Caster gets Speed II — immune to acid"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &e"+cfgI(type,2,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &e"+cfgI(type,2,"cooldown")+"s"));
            }
            case "water" -> {
                lore.add(leg("&9&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :"));
                lore.add(leg("&9💧 &8Unlimited water breathing"));
                lore.add(leg("&9💧 &8Dolphin's Grace II — always active"));
                lore.add(leg("&9💧 &8Boat speed 2x faster than normal"));
                lore.add(leg("&9💧 &810% chance to drown enemy on hit for 3s"));
                lore.add(leg("&9💧 &8Depth Strider removed from boots"));
                lore.add(leg(""));
                lore.add(leg("&9&lᴀʙɪʟɪᴛʏ ɪ &8— &9&lWater Beam"));
                lore.add(leg("&9💧 &8Trident beam — 5 hearts, bypasses all armour"));
                lore.add(leg("   &8Right-click to fire — one shot per activation"));
                lore.add(leg("   &8Trident surrounded by water particles"));
                lore.add(leg("&9💧 &840% chance to drown enemy on hit for 5s"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &9" + cfgI(type,1,"duration") + "s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &9" + cfgI(type,1,"cooldown") + "s"));
                lore.add(leg(""));
                lore.add(leg("&9&lᴀʙɪʟɪᴛʏ ɪɪ &8— &9&lAqua Form"));
                lore.add(leg("&9💧 &8Must be in water to activate"));
                lore.add(leg("&9💧 &8Strength II + Full Invisibility (armour hidden)"));
                lore.add(leg("&9💧 &8Melee hits apply Poison + Blindness for 3s"));
                lore.add(leg("&9💧 &8Pulls all entities within 15 blocks every 15s"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &9" + cfgI(type,2,"duration") + "s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &9" + cfgI(type,2,"cooldown") + "s"));
            }
            case "earth" -> {
                lore.add(leg("&a&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&a🪨 &8Haste II — always active"));
                lore.add(leg("&a🪨 &850% reduced cooldowns on all items & tools"));
                lore.add(leg("&a🪨 &83x XP gained from all sources"));
                lore.add(leg("&a🪨 &8Auto Fortune III + Unbreaking III on held tool"));
                lore.add(leg("   &8(Only applied to tools that support it)"));
                lore.add(leg("&a🪨 &8Speed I while standing on Grass or Dirt")); lore.add(leg(""));
                lore.add(leg("&a&lᴀʙɪʟɪᴛʏ ɪ &8— &a&lEarth Blessing"));
                lore.add(leg("&a🪨 &890% discount on all villager trades"));
                lore.add(leg("&a🪨 &8Vein miner while sneaking — mines full ore vein"));
                lore.add(leg("&a🪨 &8All food eaten grants Absorption I for 20s"));
                lore.add(leg("&a🪨 &8+5 extra hearts — removed on death/leave/swap"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &a"+cfgI(type,1,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &a"+cfgI(type,1,"cooldown")+"s")); lore.add(leg(""));
                lore.add(leg("&a&lᴀʙɪʟɪᴛʏ ɪɪ &8— &a&lEarth Shatter"));
                lore.add(leg("&a🪨 &83x3 area mining — breaks all blocks around you"));
                lore.add(leg("&a🪨 &8Vein miner while sneaking — mines full ore vein"));
                lore.add(leg("&a🪨 &8ALL ores auto-smelt on break (3x3, vein, normal)"));
                lore.add(leg("   &8Iron → Ingot, Gold → Ingot, Copper → Ingot"));
                lore.add(leg("&a🪨 &8Eating a Golden Apple gives Enchanted effects"));
                lore.add(leg("   &8(Regen II 20s, Resistance I 5m, Fire Res 5m, Absorb IV 2m)"));
                lore.add(leg("   &8(One time use per activation)"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &a"+cfgI(type,2,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &a"+cfgI(type,2,"cooldown")+"s"));
            }
            case "warden" -> {
                lore.add(leg("&5&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&5💀 &8Night Vision — always active")); lore.add(leg(""));
                lore.add(leg("&5&lᴀʙɪʟɪᴛʏ ɪ &8— &5Warden Domain"));
                lore.add(leg("&5💀 &8Bedrock cage rises in a full circle around you"));
                lore.add(leg("   &8🧱 Solid walls — floor to ceiling, fully enclosed"));
                lore.add(leg("   &8🧱 Animated — rings rise block by block"));
                lore.add(leg("   &8💪 Strength II inside the domain"));
                lore.add(leg("   &8🐌 Enemies: Slowness + Glowing + Darkness"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &5"+cfgI(type,1,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &5"+cfgI(type,1,"cooldown")+"s")); lore.add(leg(""));
                lore.add(leg("&5&lᴀʙɪʟɪᴛʏ ɪɪ &8— &5Warden Morph"));
                lore.add(leg("&5💀 &8Transform into a full Warden"));
                lore.add(leg("   &820 hearts — Resistance II — Strength I"));
                lore.add(leg("   &8Tremor sense detects & reveals nearby enemies"));
                lore.add(leg("   &8&nShift + Left Click&8 → &dSonic Beam &8(once per morph)"));
                lore.add(leg("   &8  ▸ Armour-bypassing beam — 15 blocks"));
                lore.add(leg("   &8  ▸ Heavy knockback + Darkness + Slowness"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &5"+cfgI(type,2,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &5"+cfgI(type,2,"cooldown")+"s"));
            }
            case "shadow" -> {
                lore.add(leg("&8&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&8👁 &8Permanent Invisibility — always active")); lore.add(leg(""));
                lore.add(leg("&8&lᴀʙɪʟɪᴛʏ ɪ &8— &8Shadow Strike"));
                lore.add(leg("&8👁 &8Deep invisibility — armour fully hidden"));
                lore.add(leg("&8👁 &8Every 10th hit blinds target for 5s"));
                lore.add(leg("&8👁 &8Arrows: 30% chance to reveal nearby players"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &7"+cfgI(type,1,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &7"+cfgI(type,1,"cooldown")+"s")); lore.add(leg(""));
                lore.add(leg("&8&lᴀʙɪʟɪᴛʏ ɪɪ &8— &8Dark Zone"));
                lore.add(leg("&8👁 &810-block darkness zone — centered on you"));
                lore.add(leg("&8👁 &8Enemies: Blindness + Weakness + Slowness"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &7"+cfgI(type,2,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &7"+cfgI(type,2,"cooldown")+"s"));
            }
            case "frost" -> {
                lore.add(leg("&f&lᴘᴀꜱꜱɪᴠᴇ ᴇꜰꜰᴇᴄᴛꜱ :")); lore.add(leg("&f❄ &8Speed I — always active"));
                lore.add(leg("&f❄ &8Speed III while on ice or snow blocks"));
                lore.add(leg("&f❄ &830% chance to fully freeze enemy on hit for 5s"));
                lore.add(leg("   &8Frozen: can't move or jump, takes tick damage")); lore.add(leg(""));
                lore.add(leg("&f&lᴀʙɪʟɪᴛʏ ɪ &8— &f&lFrost Bomb"));
                lore.add(leg("&f❄ &8Receive a special &f&l❄ Frost Bomb &8snowball"));
                lore.add(leg("   &8Unlimited throws while ability is active"));
                lore.add(leg("   &8Each throw spawns a large packed ice iceberg"));
                lore.add(leg("   &8Enemies inside the iceberg get fully frozen"));
                lore.add(leg("   &8Snowball removed when ability ends"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &f"+cfgI(type,1,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &f"+cfgI(type,1,"cooldown")+"s")); lore.add(leg(""));
                lore.add(leg("&f&lᴀʙɪʟɪᴛʏ ɪɪ &8— &f&lIce Trail + Ice Prison"));
                lore.add(leg("&f❄ &8Packed ice forms under your feet as you walk"));
                lore.add(leg("   &8Walk on air — ice bridges form beneath you"));
                lore.add(leg("   &8Does not affect bedrock"));
                lore.add(leg("   &850% chance to encase hit enemies in packed ice"));
                lore.add(leg("   &8Prisoner levitates inside + takes 2 hearts every 10t"));
                lore.add(leg("   &8Prison is unbreakable — lasts 10 seconds"));
                lore.add(leg("&9ᴅᴜʀᴀᴛɪᴏɴ : &f"+cfgI(type,2,"duration")+"s"));
                lore.add(leg("&9ᴄᴏᴏʟᴅᴏᴡɴ : &f"+cfgI(type,2,"cooldown")+"s"));
            }
        }
        lore.add(leg("")); return lore;
    }
    // ── Fire — Lava Damage Ramp ───────────────────────────────────────────

    private boolean isInLava(Player p) {
        return p.getLocation().getBlock().getType() == Material.LAVA
                || p.getLocation().clone().add(0,1,0).getBlock().getType() == Material.LAVA;
    }
    private void applyLavaDamageLevel(Player p, int level) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE); if (a==null) return;
        a.removeModifier(FIRE_LAVA_KEY); if (level<=0) return;
        double bonus = plugin.getConfig().getDouble("spheres.fire.passive.lava-damage-per-level",3.0)*level;
        a.addModifier(new AttributeModifier(FIRE_LAVA_KEY, bonus, AttributeModifier.Operation.ADD_NUMBER));
    }
    private void removeLavaDamageModifier(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (a!=null) a.removeModifier(FIRE_LAVA_KEY);
    }
    private void startFireLavaTask(Player p) {
        UUID uuid = p.getUniqueId(); cancelTaskByUUID(fireLavaTasks,uuid); fireLavaLevel.put(uuid,0);
        final int[] ticksIn={0};
        final int maxLvl = plugin.getConfig().getInt("spheres.fire.passive.lava-max-level",3);
        final int tpl = plugin.getConfig().getInt("spheres.fire.passive.lava-ticks-per-level",1200);
        fireLavaTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { resetFireLava(p); return; }
            ItemStack off = p.getInventory().getItemInOffHand();
            if (!isSphere(off)||!"fire".equals(getSphereType(off))) { resetFireLava(p); return; }
            if (!isInLava(p)) { if (fireLavaLevel.getOrDefault(uuid,0)>0) {
                fireLavaLevel.put(uuid,0); ticksIn[0]=0; removeLavaDamageModifier(p);
                p.sendMessage(leg("&c&lLava Rage &7— Damage bonus lost.")); } ticksIn[0]=0; return; }
            ticksIn[0]++; int cur = fireLavaLevel.getOrDefault(uuid,0);
            int nl = Math.min(ticksIn[0]/tpl, maxLvl);
            if (nl>cur) { fireLavaLevel.put(uuid,nl); applyLavaDamageLevel(p,nl);
                String[] n={"","I","II","III"};
                p.sendMessage(leg("&c&lLava Rage &7— Strength &c"+(nl<=3?n[nl]:"III")+" &7active!"));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0,1,0), 30, 0.4,0.5,0.4, 0.08);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.8f); }
        }, 0L, 1L));
    }
    private void resetFireLava(Player p) {
        cancelTaskByUUID(fireLavaTasks, p.getUniqueId());
        fireLavaLevel.remove(p.getUniqueId()); removeLavaDamageModifier(p);
    }

    // ── Wind — Speed Ramp ─────────────────────────────────────────────────

    private void startWindSpeedRamp(Player p) {
        UUID uuid = p.getUniqueId(); cancelTaskByUUID(windSpeedTasks,uuid);
        windSpeedLevel.put(uuid,1); setWindSpeedAttribute(p,0);
        final int tpr = plugin.getConfig().getInt("spheres.wind.passive.speed-ramp-ticks",500);
        windSpeedTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { resetWindSpeed(p); return; }
            ItemStack off = p.getInventory().getItemInOffHand();
            if (!isSphere(off)||!"wind".equals(getSphereType(off))) { resetWindSpeed(p); return; }
            int cur = windSpeedLevel.getOrDefault(uuid,1);
            if (cur<5) { int nx = cur+1; windSpeedLevel.put(uuid,nx); setWindSpeedAttribute(p,nx-1);
                String[] n={"I","II","III","IV","V"}; p.sendMessage(leg("&b&lWind &7— Speed &b"+n[nx-1]+"&7!")); }
        }, tpr, tpr));
    }
    private void setWindSpeedAttribute(Player p, int bonus) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED); if (a==null) return;
        a.removeModifier(WIND_SPEED_KEY); if (bonus<=0) return;
        a.addModifier(new AttributeModifier(WIND_SPEED_KEY, bonus*0.02, AttributeModifier.Operation.ADD_NUMBER));
    }
    private void resetWindSpeed(Player p) {
        cancelTaskByUUID(windSpeedTasks, p.getUniqueId()); windSpeedLevel.put(p.getUniqueId(),1);
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (a!=null) a.removeModifier(WIND_SPEED_KEY);
    }

    // ── Frost — Ice Prison ────────────────────────────────────────────────

    private void createIcePrison(Player attacker, Player victim) {
        UUID vid = victim.getUniqueId(); if (icePrisonBlocks.containsKey(vid)) return;
        Location center = victim.getLocation().clone(); World w = center.getWorld();
        List<Location> blocks = new ArrayList<>();
        for (int x=-1;x<=1;x++) for (int y=0;y<=3;y++) for (int z=-1;z<=1;z++) {
            boolean wall=(x==-1||x==1||z==-1||z==1), topBot=(y==0||y==3);
            if (!wall&&!topBot) continue; Location bl = center.clone().add(x,y,z);
            bl.getBlock().setType(Material.PACKED_ICE);
            bl.getBlock().setMetadata("frostPrison", new FixedMetadataValue(plugin, vid.toString()));
            blocks.add(bl.clone()); }
        icePrisonBlocks.put(vid, blocks);
        icePrisonMap.put(center.getBlock().getLocation(), vid);
        Location inside = center.clone().add(0.5,1.0,0.5);
        inside.setYaw(victim.getLocation().getYaw()); inside.setPitch(victim.getLocation().getPitch());
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!victim.isOnline()) return;
            victim.teleport(inside); victim.setFreezeTicks(Integer.MAX_VALUE);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,300,255,false,false,false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,300,-10,false,false,false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,300,0,false,false,false));
            victim.sendMessage(leg("&f&lFrost &7— You are &bencased in ice&7!"));
            w.spawnParticle(Particle.SNOWFLAKE, inside, 80, 1,1,1, 0.1);
            w.playSound(center, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
            w.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.0f); }, 1L);
        BukkitTask lock = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isOnline()||!icePrisonBlocks.containsKey(vid)) return;
            Location cur = victim.getLocation();
            if (Math.abs(cur.getX()-(center.getX()+0.5))>0.6||Math.abs(cur.getZ()-(center.getZ()+0.5))>0.6)
                victim.teleport(inside);
            victim.setFreezeTicks(Integer.MAX_VALUE);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,255,false,false,false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,40,-10,false,false,false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,40,0,false,false,false));
            victim.damage(4.0);
            w.spawnParticle(Particle.SNOWFLAKE, victim.getLocation(), 5, 0.3,0.3,0.3, 0.02); }, 3L, 10L);
        icePrisonDmgTasks.put(vid, lock);
        int dur = plugin.getConfig().getInt("spheres.frost.ability2.prison-duration-ticks",200);
        Bukkit.getScheduler().runTaskLater(plugin, () -> releaseIcePrison(vid), dur);
    }
    private void releaseIcePrison(UUID vid) {
        List<Location> blocks = icePrisonBlocks.remove(vid);
        if (blocks!=null) for (Location bl : blocks) { bl.getBlock().removeMetadata("frostPrison",plugin);
            if (bl.getBlock().getType()==Material.PACKED_ICE) bl.getBlock().setType(Material.AIR); }
        cancelTaskByUUID(icePrisonDmgTasks, vid);
        icePrisonMap.values().removeIf(v -> v.equals(vid));
        Player victim = Bukkit.getPlayer(vid);
        if (victim!=null&&victim.isOnline()) { victim.setFreezeTicks(0);
            victim.removePotionEffect(PotionEffectType.SLOWNESS);
            victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
            victim.removePotionEffect(PotionEffectType.LEVITATION);
            victim.sendMessage(leg("&f&lFrost &7— You have been &areleased&7!"));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation(), 30, 0.5,0.5,0.5, 0.05); }
    }

    // ── Commands ──────────────────────────────────────────────────────────

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("cooldown")) {
            if (!p.hasPermission("spheresmp.admin")) { msg(p,"no-permission"); return true; }
            clearAllCooldowns(p); return true; }
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (!isSphere(offhand)) { msg(p,"sphere-not-held"); return true; }
        String type = getSphereType(offhand); if (type==null) return true;
        triggerAbility(p, type, cmd.equals("ability1")?1:2); return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return List.of(); }

    private void clearAllCooldowns(Player p) {
        UUID uuid=p.getUniqueId(); String ustr=uuid.toString(); boolean found=false;
        for (String key : new ArrayList<>(activeMap.keySet()).stream().filter(k->k.startsWith(ustr)).toList()) {
            String[] sp = key.replace(ustr+":","").split(":");
            if (sp.length==2) try { executeAbility(p,sp[0],Integer.parseInt(sp[1]),false); } catch (Exception ignored) {}
            activeMap.remove(key); cancelTaskByKey(durationTasks,key); found=true; }
        for (String key : new ArrayList<>(cooldownMap.keySet()).stream().filter(k->k.startsWith(ustr)).toList()) {
            cooldownMap.remove(key); cancelTaskByKey(cooldownTasks,key); found=true; }
        cancelTaskByUUID(fireBeamTasks,uuid); cancelTaskByUUID(shadowZoneTasks,uuid);
        cancelTaskByUUID(actionBarTasks,uuid); resetFireLava(p); p.sendActionBar(Component.empty());
        p.sendMessage(leg(found?"&a&lCooldowns Cleared &7— All sphere cooldowns reset.":"&7You have no active cooldowns."));
    }

    // ── Ability Trigger / Cooldown / Action Bar ───────────────────────────

    private void triggerAbility(Player p, String type, int num) {
        String key = abilityKey(p.getUniqueId(),type,num);
        if (activeMap.containsKey(key)) { msg(p,"ability-active"); return; }
        if (cooldownMap.containsKey(key)) { msg(p,"ability-on-cooldown"); return; }
        boolean hasDur = plugin.getConfig().contains("spheres."+type+".ability"+num+".duration");
        if (!hasDur) { executeAbility(p,type,num,true); startCooldown(p,type,num); refreshActionBar(p,type); return; }
        int dur = cfgI(type,num,"duration"); activeMap.put(key,dur);
        executeAbility(p,type,num,true); refreshActionBar(p,type);
        BukkitTask dt = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left=dur; @Override public void run() { left--; activeMap.put(key,left);
                if (left<=0) { executeAbility(p,type,num,false); activeMap.remove(key);
                    cancelTaskByKey(durationTasks,key); startCooldown(p,type,num); refreshActionBar(p,type); } }
        }, 20L, 20L);
        cancelTaskByKey(durationTasks,key); durationTasks.put(key,dt);
    }
    private void startCooldown(Player p, String type, int num) {
        String key=abilityKey(p.getUniqueId(),type,num); int sec=cfgI(type,num,"cooldown");
        cooldownMap.put(key,sec); refreshActionBar(p,type);
        BukkitTask ct = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left=sec; @Override public void run() { left--;
                if (left<=0) { cooldownMap.remove(key); cancelTaskByKey(cooldownTasks,key);
                    if (p.isOnline()) refreshActionBar(p,type); } else cooldownMap.put(key,left); }
        }, 20L, 20L);
        cancelTaskByKey(cooldownTasks,key); cooldownTasks.put(key,ct);
    }
    private void refreshActionBar(Player p, String type) {
        UUID uuid=p.getUniqueId(); cancelTaskByUUID(actionBarTasks,uuid);
        actionBarTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { cancelTaskByUUID(actionBarTasks,uuid); return; }
            if (!isSphere(p.getInventory().getItemInOffHand())) {
                cancelTaskByUUID(actionBarTasks,uuid); p.sendActionBar(Component.empty()); return; }
            String sym=plugin.getConfig().getString("action-bar.symbol-color","&e");
            String lbl=plugin.getConfig().getString("action-bar.label-color","&f");
            String rdy=plugin.getConfig().getString("action-bar.ready-color","&a");
            String act=plugin.getConfig().getString("action-bar.active-color","&e");
            String cd=plugin.getConfig().getString("action-bar.cooldown-color","&c");
            String sep=plugin.getConfig().getString("action-bar.separator"," &8| ");
            p.sendActionBar(leg(sym+"⚡ "+lbl+"A1: "+abilityStatus(uuid,type,1,rdy,act,cd)
                    +sep+sym+"⚡ "+lbl+"A2: "+abilityStatus(uuid,type,2,rdy,act,cd)));
        }, 0L, 20L));
    }
    private String abilityStatus(UUID u, String t, int n, String rdy, String act, String cd) {
        String k=abilityKey(u,t,n);
        if (activeMap.containsKey(k)) return act+"ᴀᴄᴛɪᴠᴇ ("+activeMap.get(k)+"ꜱ)";
        if (cooldownMap.containsKey(k)) return cd+"ᴄᴅ ("+cooldownMap.get(k)+"ꜱ)";
        return rdy+"ʀᴇᴀᴅʏ";
    }
    private void executeAbility(Player p, String type, int num, boolean start) {
        switch (type) {
            case "fire"->abilityFire(p,num,start); case "wind"->abilityWind(p,num,start);
            case "volt"->abilityVolt(p,num,start); case "water"->abilityWater(p,num,start);
            case "earth"->abilityEarth(p,num,start); case "warden"->abilityWarden(p,num,start);
            case "shadow"->abilityShadow(p,num,start); case "frost"->abilityFrost(p,num,start);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  FIRE — Ability 2 rewritten as Infernal Flamethrower
    // ═════════════════════════════════════════════════════════════════════

    private void abilityFire(Player p, int num, boolean start) {
        if (num == 1 && start) {
            Location loc = p.getLocation(); World world = p.getWorld();
            double radius = cfgD("fire",1,"explosion-radius"); long restore = 300L;
            for (int x=-(int)radius;x<=(int)radius;x++) for (int z=-(int)radius;z<=(int)radius;z++) {
                double dist=Math.sqrt(x*x+z*z); if (dist>radius) continue;
                Location floor=loc.clone().add(x,-1,z); Material origF=floor.getBlock().getType();
                if (!origF.isAir()&&origF.isSolid()) { Material rep=(Math.random()<0.38)?Material.MAGMA_BLOCK:Material.NETHERRACK;
                    floor.getBlock().setType(rep); Bukkit.getScheduler().runTaskLater(plugin,()->floor.getBlock().setType(origF),restore); }
                Location surface=loc.clone().add(x,0,z); Material origS=surface.getBlock().getType();
                if (!origS.isAir()) { Material rep=dist<radius*0.42?Material.MAGMA_BLOCK:Material.NETHERRACK;
                    surface.getBlock().setType(rep); Bukkit.getScheduler().runTaskLater(plugin,()->surface.getBlock().setType(origS),restore);
                } else if (dist<radius*0.45) { surface.getBlock().setType(Material.FIRE);
                    Bukkit.getScheduler().runTaskLater(plugin,()->{if(surface.getBlock().getType()==Material.FIRE) surface.getBlock().setType(Material.AIR);},restore); } }
            world.spawnParticle(Particle.EXPLOSION_EMITTER,loc,4,1.5,0.5,1.5,0);
            world.spawnParticle(Particle.FLAME,loc,200,3,1,3,0.22);
            world.spawnParticle(Particle.LAVA,loc,80,2,0.5,2,0);
            world.spawnParticle(Particle.LARGE_SMOKE,loc,100,3,1,3,0.08);
            world.playSound(loc,Sound.ENTITY_GENERIC_EXPLODE,2f,0.6f);
            world.playSound(loc,Sound.ENTITY_BLAZE_DEATH,1.5f,0.5f);
            world.playSound(loc,Sound.BLOCK_FIRE_AMBIENT,1f,0.8f);
            List<LivingEntity> targets = new ArrayList<>();
            for (Entity en : world.getNearbyEntities(loc,radius,radius,radius)) {
                if (en.equals(p)) continue;
                if (en instanceof LivingEntity le) { targets.add(le); le.setFireTicks(120); le.damage(10.0,p); } }
            Bukkit.getScheduler().runTaskLater(plugin, () -> { for (LivingEntity le : targets) {
                if (!le.isValid()||le.isDead()) continue;
                Vector away = le.getLocation().toVector().subtract(loc.toVector());
                if (away.lengthSquared()<0.01) away=new Vector(0.3,0,0.3);
                away=away.normalize().multiply(1.5); away.setY(3.8); le.setVelocity(away); } }, 2L);
        }

// ══════ ABILITY 2 — INFERNAL FLAMETHROWER (NO KB / NO BLAST) ══════
        if (num == 2) {
            if (start) {
                UUID uuid = p.getUniqueId();
                World world = p.getWorld();

                double range     = plugin.getConfig().getDouble("spheres.fire.ability2.beam-range", 12.0);
                double dmg       = plugin.getConfig().getDouble("spheres.fire.ability2.beam-damage", 2.8);
                double beamWidth = plugin.getConfig().getDouble("spheres.fire.ability2.beam-width", 0.7);
                int fireTicks    = plugin.getConfig().getInt("spheres.fire.ability2.fire-ticks", 80);
                int tickRate     = plugin.getConfig().getInt("spheres.fire.ability2.beam-tick-rate", 1);

                // Activation sound
                world.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 2f, 0.6f);
                world.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);

                BukkitTask beam = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

                    if (!p.isOnline()) return;

                    // 🔥 ORIGIN FROM CHEST / STOMACH
                    Location origin = p.getLocation().clone().add(0, 1.0, 0);
                    Vector dir = p.getLocation().getDirection().normalize();
                    World w = origin.getWorld();

                    // Core emission particles
                    w.spawnParticle(Particle.FLAME, origin, 3, 0.2,0.2,0.2, 0);
                    w.spawnParticle(Particle.LAVA, origin, 2, 0.15,0.15,0.15, 0);

                    for (double d = 0.45; d <= range; d += 0.45) {

                        Location point = origin.clone().add(dir.clone().multiply(d));

                        if (!point.getBlock().getType().isAir()
                                && point.getBlock().getType().isSolid()) {
                            break; // stop at wall
                        }

                        double width = beamWidth * (1.0 - (d / range) * 0.25);

                        double jitterX = (Math.random() - 0.5) * 0.10;
                        double jitterY = (Math.random() - 0.5) * 0.10;
                        double jitterZ = (Math.random() - 0.5) * 0.10;

                        Location visual = point.clone().add(jitterX, jitterY, jitterZ);

                        // Thick flame body
                        w.spawnParticle(Particle.FLAME, visual, 2,
                                width*0.2, width*0.2, width*0.2, 0.005);

                        // Inner blue-hot core
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, visual, 1,
                                width*0.1, width*0.1, width*0.1, 0);

                        // Light smoke trail
                        if (Math.random() < 0.15)
                            w.spawnParticle(Particle.SMOKE, visual, 1,
                                    width*0.15, width*0.15, width*0.15, 0);

// Damage entities (real sword-style knockback)
                        for (Entity en : w.getNearbyEntities(point, beamWidth, beamWidth, beamWidth)) {

                            if (!(en instanceof LivingEntity le)) continue;
                            if (le.equals(p)) continue;

                            // Prevent damage spam every tick
                            if (le.getNoDamageTicks() <= 0) {

                                le.damage(dmg, p);          // real Minecraft damage
                                le.setFireTicks(fireTicks); // burn effect

                                // Light burn particles
                                w.spawnParticle(Particle.FLAME,
                                        le.getLocation().add(0,1,0),
                                        6, 0.4,0.4,0.4, 0.08);
                            }
                        }
                    }

                }, 0L, tickRate);

                cancelTaskByUUID(fireBeamTasks, uuid);
                fireBeamTasks.put(uuid, beam);

            } else {
                cancelTaskByUUID(fireBeamTasks, p.getUniqueId());
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.7f);
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                        p.getLocation().add(0,1,0),
                        20, 0.5,0.5,0.5, 0.04);
            }
        }
    }

    // ── WIND (unchanged) ──────────────────────────────────────────────────

    private void abilityWind(Player p, int num, boolean start) {
        if (num==1&&start) {
            World world=p.getWorld();
            double upPow=plugin.getConfig().getDouble("spheres.wind.ability1.jump-power",1.4);
            double fwPow=plugin.getConfig().getDouble("spheres.wind.ability1.dash-power",1.8);
            Vector dir=p.getLocation().getDirection().normalize();
            if (p.getLocation().getBlock().getType()==Material.COBWEB) p.getLocation().getBlock().setType(Material.AIR);
            p.setVelocity(new Vector(dir.getX()*fwPow,upPow,dir.getZ()*fwPow)); p.setFallDistance(0);
            world.spawnParticle(Particle.CLOUD,p.getLocation(),40,0.5,0.2,0.5,0.18);
            world.spawnParticle(Particle.END_ROD,p.getLocation(),15,0.6,0.3,0.6,0.2);
            for (int i=1;i<=25;i++) { final int fi=i; Bukkit.getScheduler().runTaskLater(plugin,()->{
                if (!p.isOnline()) return; world.spawnParticle(Particle.CLOUD,p.getLocation(),6,0.25,0.15,0.25,0.04);},fi); }
            world.playSound(p.getLocation(),Sound.ENTITY_BREEZE_IDLE_AIR,2f,1.0f);
            world.playSound(p.getLocation(),Sound.ENTITY_BREEZE_JUMP,1f,1.0f);
        }
        if (num==2&&start) {
            Location loc=p.getLocation(); World world=p.getWorld();
            double radius=cfgD("wind",2,"knockback-radius");
            double launchPw=plugin.getConfig().getDouble("spheres.wind.ability2.launch-power",3.0);
            double landDmg=plugin.getConfig().getDouble("spheres.wind.ability2.land-damage",8.0);
            int holdTick=plugin.getConfig().getInt("spheres.wind.ability2.hold-ticks",40);
            int charges=plugin.getConfig().getInt("spheres.wind.ability2.wind-charge-count",15);
            int chargeDur=plugin.getConfig().getInt("spheres.wind.ability2.wind-charge-duration",20);
            world.spawnParticle(Particle.CLOUD,loc,200,5,3,5,0.4);
            world.spawnParticle(Particle.SWEEP_ATTACK,loc,40,4,1,4,0);
            world.spawnParticle(Particle.END_ROD,loc,60,4,2,4,0.3);
            world.playSound(loc,Sound.ENTITY_RAVAGER_ROAR,2f,0.7f);
            world.playSound(loc,Sound.ENTITY_BREEZE_IDLE_AIR,1f,0.6f);
            List<LivingEntity> targets=new ArrayList<>();
            for (Entity en:world.getNearbyEntities(loc,radius,radius,radius))
                if (!en.equals(p)&&en instanceof LivingEntity le) targets.add(le);
            for (LivingEntity le:targets) { le.setVelocity(new Vector((Math.random()-0.5)*0.3,launchPw,(Math.random()-0.5)*0.3));
                le.setFallDistance(-999); if (le instanceof Player tp) tp.sendMessage(leg("&b&lWind &7— You've been launched!")); }
            for (int tick=5;tick<holdTick;tick+=2) { final int ft=tick;
                Bukkit.getScheduler().runTaskLater(plugin,()->{for (LivingEntity le:targets) {
                    if (!le.isValid()||le.isDead()) continue; Vector v=le.getVelocity();
                    if (v.getY()<0&&ft<holdTick-5) le.setVelocity(new Vector(v.getX()*0.5,0.1,v.getZ()*0.5));}},ft); }
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                for (LivingEntity le:targets) { if (!le.isValid()||le.isDead()) continue;
                    le.setVelocity(new Vector(0,-4.5,0)); le.setFallDistance(0); }
                Bukkit.getScheduler().runTaskLater(plugin,()->{for (LivingEntity le:targets) {
                    if (!le.isValid()||le.isDead()) continue; le.damage(landDmg,p); le.setFallDistance(0);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER,le.getLocation(),2,0.5,0,0.5,0);
                    world.playSound(le.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1.5f,1.2f);
                    if (le instanceof Player tp) tp.sendMessage(leg("&c&lCrash! &7Took &c"+(int)(landDmg/2)+" &7hearts."));}},15L);
            }, holdTick);
            UUID uuid=p.getUniqueId(); windCharges.put(uuid,charges); cancelTaskByUUID(windChargeTasks,uuid);
            windChargeTasks.put(uuid, Bukkit.getScheduler().runTaskLater(plugin,()->{
                windCharges.remove(uuid); if (p.isOnline()) p.sendMessage(leg("&7Wind charges expired."));
            },(chargeDur*20L)/2));
            p.sendMessage(leg("&b&lRavager Roar &7— &b"+charges+" &7wind charges granted."));
        }
    }

    // ── VOLT (unchanged) ──────────────────────────────────────────────────

    private void abilityVolt(Player p, int num, boolean start) {
        if (num==1) { if (start) { UUID uuid=p.getUniqueId(); World world=p.getWorld();
            int radius=plugin.getConfig().getInt("spheres.volt.ability1.lightning-radius",40);
            int interval=plugin.getConfig().getInt("spheres.volt.ability1.lightning-interval",3);
            double strDmg=plugin.getConfig().getDouble("spheres.volt.ability1.strike-damage",6.0);
            int stunDur=plugin.getConfig().getInt("spheres.volt.ability1.stun-duration",60);
            int gridStep=plugin.getConfig().getInt("spheres.volt.ability1.lightning-grid-step",7);
            voltLightningImmune.add(uuid);
            p.setMetadata("voltThunderApoc",new FixedMetadataValue(plugin,true));
            world.setStorm(true); world.setThundering(true);
            world.spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation(),100,3,3,3,0.5);
            world.playSound(p.getLocation(),Sound.ENTITY_LIGHTNING_BOLT_THUNDER,2f,0.6f);
            p.sendMessage(leg("&e&lThunder Apocalypse &7— Active!"));
            BukkitTask at = Bukkit.getScheduler().runTaskTimer(plugin,()->{if (!p.isOnline()) return;
                Location center=p.getLocation();
                for (int x=-radius;x<=radius;x+=gridStep) for (int z=-radius;z<=radius;z+=gridStep) {
                    if (Math.sqrt(x*x+z*z)>radius) continue;
                    double ox=x+(Math.random()*gridStep-gridStep/2.0);
                    double oz=z+(Math.random()*gridStep-gridStep/2.0);
                    Location strike=center.clone().add(ox,0,oz); strike.setY(world.getHighestBlockYAt(strike));
                    world.strikeLightningEffect(strike); }
                for (Entity en:world.getNearbyEntities(center,radius,radius,radius)) {
                    if (en.equals(p)||!(en instanceof LivingEntity le)) continue;
                    le.damage(strDmg,p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,stunDur,3,false,false,true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,stunDur,-10,false,false,false)); }
            },0L,interval);
            cancelTaskByUUID(thunderApocTasks,uuid); thunderApocTasks.put(uuid,at);
        } else { UUID uuid=p.getUniqueId(); p.removeMetadata("voltThunderApoc",plugin);
            voltLightningImmune.remove(uuid); cancelTaskByUUID(thunderApocTasks,uuid);
            p.getWorld().setStorm(false); p.getWorld().setThundering(false);
            p.getWorld().setClearWeatherDuration(12000);
            p.sendMessage(leg("&e&lThunder Apocalypse &7— Ended. Weather cleared.")); } }
        if (num==2) { if (start) { UUID uuid=p.getUniqueId(); World world=p.getWorld();
            int totalDur=plugin.getConfig().getInt("spheres.volt.ability2.duration",30);
            int lightDur=plugin.getConfig().getInt("spheres.volt.ability2.lightning-duration",15);
            double lightRad=plugin.getConfig().getDouble("spheres.volt.ability2.lightning-radius",15.0);
            double acidDmg=plugin.getConfig().getDouble("spheres.volt.ability2.acid-damage",1.0);
            int acidInt=plugin.getConfig().getInt("spheres.volt.ability2.acid-interval",20);
            world.setStorm(true); world.setThundering(true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,totalDur*20,1,false,false,true));
            acidRainActive.add(uuid);
            BukkitTask lt=Bukkit.getScheduler().runTaskTimer(plugin,()->{if (!p.isOnline()) return;
                nearbyPlayers(p,lightRad).forEach(tp->world.strikeLightning(tp.getLocation()));},0L,30L);
            Bukkit.getScheduler().runTaskLater(plugin,lt::cancel,lightDur*20L);
            BukkitTask at=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if (!p.isOnline()||!acidRainActive.contains(uuid)) return;
                for (Player tp:Bukkit.getOnlinePlayers()) { if (tp.equals(p)||!tp.getWorld().equals(world)) continue;
                    if (!world.hasStorm()||!isExposedToSky(tp)) continue;
                    tp.setHealth(Math.max(0,tp.getHealth()-acidDmg));
                    tp.getWorld().spawnParticle(Particle.DRIPPING_WATER,tp.getLocation().add(0,2.2,0),6,0.4,0,0.4,0.02);
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.POISON,acidInt+2,0,false,false,true)); }
            },0L,acidInt);
            cancelTaskByUUID(acidRainTasks,uuid); acidRainTasks.put(uuid,at);
            world.playSound(p.getLocation(),Sound.ENTITY_LIGHTNING_BOLT_THUNDER,2f,0.5f);
            p.sendMessage(leg("&e&lAcid Rain &7— Active for &e"+totalDur+"s&7!"));
        } else { UUID uuid=p.getUniqueId(); acidRainActive.remove(uuid); cancelTaskByUUID(acidRainTasks,uuid);
            p.removePotionEffect(PotionEffectType.SPEED); p.getWorld().setStorm(false);
            p.getWorld().setThundering(false); p.getWorld().setClearWeatherDuration(12000);
            p.sendMessage(leg("&e&lAcid Rain &7— Ended.")); } }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  WATER — Completely Rewritten
    // ═════════════════════════════════════════════════════════════════════

    private void abilityWater(Player p, int num, boolean start) {
        UUID uuid = p.getUniqueId();

        // ══════ ABILITY 1 — WATER BEAM (Trident, one shot per activation) ══════
        if (num == 1) {
            if (start) {
                waterBeamUsed.remove(uuid);
                p.setMetadata("sphereWaterBeam", new FixedMetadataValue(plugin, true));
                p.sendMessage(leg("&9&lWater Beam &7— Active! Right-click to fire trident."));
                p.sendMessage(leg("&740% drowning chance on melee hit. One beam per activation."));
            } else {
                waterBeamUsed.remove(uuid);
                p.removeMetadata("sphereWaterBeam", plugin);
            }
        }

        // ══════ ABILITY 2 — AQUA FORM ══════
        if (num == 2) {
            if (start) {
                boolean inWater = p.isInWater()
                        || p.getLocation().getBlock().getType() == Material.WATER
                        || p.getLocation().clone().add(0,-1,0).getBlock().getType() == Material.WATER;
                if (!inWater) {
                    msg(p, "water-ability2-land");
                    String key = abilityKey(uuid, "water", 2);
                    activeMap.remove(key); cancelTaskByKey(durationTasks, key); return;
                }
                int dur = cfgI("water", 2, "duration");
                int poisonDur = plugin.getConfig().getInt("spheres.water.ability2.poison-duration", 60);
                int blindDur  = plugin.getConfig().getInt("spheres.water.ability2.blindness-duration", 60);
                double pullRad = plugin.getConfig().getDouble("spheres.water.ability2.pull-radius", 15.0);
                int pullInt    = plugin.getConfig().getInt("spheres.water.ability2.pull-interval", 300);
                double pullStr = plugin.getConfig().getDouble("spheres.water.ability2.pull-strength", 1.5);

                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        dur*20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        dur*20, 0, false, false, false));
                hideArmorFromAll(p);
                shadowArmorHidden.add(uuid);
                p.setMetadata("sphereWaterAqua", new FixedMetadataValue(plugin, true));

                // Pull task
                BukkitTask pullTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!p.isOnline()) return;
                    World w = p.getWorld();
                    boolean pulled = false;
                    for (Entity en : w.getNearbyEntities(p.getLocation(), pullRad, pullRad, pullRad)) {
                        if (en.equals(p)) continue;
                        if (!(en instanceof LivingEntity le)) continue;
                        Vector pull = p.getLocation().toVector()
                                .subtract(le.getLocation().toVector()).normalize().multiply(pullStr);
                        pull.setY(0.3);
                        le.setVelocity(pull);
                        le.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                                le.getLocation().add(0,1,0), 12, 0.3,0.3,0.3, 0.05);
                        if (le instanceof Player tp)
                            tp.sendMessage(leg("&9&lAqua Pull &7— You are being pulled!"));
                        pulled = true;
                    }
                    if (pulled) {
                        w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.5f, 0.6f);
                        w.spawnParticle(Particle.DRIPPING_WATER, p.getLocation(),
                                40, pullRad*0.3, 2, pullRad*0.3, 0.05);
                    }
                }, pullInt, pullInt);
                cancelTaskByUUID(waterPullTasks, uuid);
                waterPullTasks.put(uuid, pullTask);

                p.sendMessage(leg("&9&lAqua Form &7— Strength II + Full Invisibility active!"));
                p.sendMessage(leg("&7Pulls all nearby entities every 15s."));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2f, 0.8f);
                p.getWorld().spawnParticle(Particle.DRIPPING_WATER, p.getLocation(), 60, 2,2,2, 0.1);
            } else {
                p.removeMetadata("sphereWaterAqua", plugin);
                p.removePotionEffect(PotionEffectType.STRENGTH);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                if (shadowArmorHidden.remove(uuid)) showArmorToAll(p);
                cancelTaskByUUID(waterPullTasks, uuid);
            }
        }
    }

    // ── EARTH (unchanged) ─────────────────────────────────────────────────

    private void abilityEarth(Player p, int num, boolean start) {
        UUID uuid=p.getUniqueId();
        if (num==1) { if (start) { earthBlessingActive.add(uuid); earthDiscountActive.add(uuid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,cfgI("earth",1,"duration")*20,4,false,false,true));
            AttributeInstance mh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (mh!=null) { mh.removeModifier(EARTH_BLESS_HEALTH_KEY);
                mh.addModifier(new AttributeModifier(EARTH_BLESS_HEALTH_KEY,10.0,AttributeModifier.Operation.ADD_NUMBER));
                p.setHealth(Math.min(p.getHealth()+10,mh.getValue())); }
            p.sendMessage(leg("&a&lEarth Blessing &7Active! +5 hearts, 90% discount, vein miner active."));
        } else { earthBlessingActive.remove(uuid); earthDiscountActive.remove(uuid);
            p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
            AttributeInstance mh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (mh!=null) { mh.removeModifier(EARTH_BLESS_HEALTH_KEY);
                if (p.getHealth()>mh.getValue()) p.setHealth(mh.getValue()); } } }
        if (num==2) { if (start) { p.setMetadata("sphereEarthShatter",new FixedMetadataValue(plugin,true));
            earthGoldenAppleUsed.remove(uuid);
            p.sendMessage(leg("&a&lEarth Shatter &73x3 mining + vein miner + auto-smelt active."));
        } else { p.removeMetadata("sphereEarthShatter",plugin); earthGoldenAppleUsed.remove(uuid); } }
    }

    // ── WARDEN SONIC BEAM (unchanged — keeping compact) ───────────────────

    private void fireWardenSonicBoom(Player p) {
        UUID uuid=p.getUniqueId(); World world=p.getWorld(); morphBoomUsed.add(uuid);
        Location eye=p.getEyeLocation(); Vector dir=eye.getDirection().normalize();
        double range=plugin.getConfig().getDouble("spheres.warden.ability2.sonic-boom-range",15.0);
        double damage=plugin.getConfig().getDouble("spheres.warden.ability2.sonic-boom-damage",12.0);
        double hitRad=plugin.getConfig().getDouble("spheres.warden.ability2.sonic-boom-hit-radius",1.8);
        double knockback=plugin.getConfig().getDouble("spheres.warden.ability2.sonic-boom-knockback",3.0);
        Vector perp1=dir.clone().crossProduct(new Vector(0,1,0));
        if (perp1.lengthSquared()<0.001) perp1=dir.clone().crossProduct(new Vector(1,0,0));
        perp1.normalize(); Vector perp2=dir.clone().crossProduct(perp1).normalize();
        final Vector fp1=perp1.clone(), fp2=perp2.clone();
        Particle.DustOptions dustP=new Particle.DustOptions(Color.fromRGB(100,0,200),1.8f);
        Particle.DustOptions dustD=new Particle.DustOptions(Color.fromRGB(30,0,80),2.2f);
        world.playSound(eye,Sound.ENTITY_WARDEN_SONIC_BOOM,2f,1f);
        world.playSound(eye,Sound.ENTITY_WARDEN_SONIC_CHARGE,1.5f,0.7f);
        world.spawnParticle(Particle.SCULK_SOUL,eye,100,2,1,2,0.08);
        world.spawnParticle(Particle.SCULK_CHARGE_POP,eye,120,2.5,1.2,2.5,0.10);
        world.spawnParticle(Particle.ELECTRIC_SPARK,eye,80,1.5,0.8,1.5,0.20);
        Set<UUID> alreadyHit=new HashSet<>(); double step=0.5; int totalSteps=(int)(range/step);
        for (int s=0;s<totalSteps;s++) { final double d=step+s*step; final int fs=s;
            Bukkit.getScheduler().runTaskLater(plugin,()->{if (!p.isOnline()) return;
                Location core=eye.clone().add(dir.clone().multiply(d));
                if (!core.getBlock().getType().isAir()&&core.getBlock().getType().isSolid()) return;
                world.spawnParticle(Particle.SCULK_SOUL,core,3,0.02,0.02,0.02,0);
                world.spawnParticle(Particle.SCULK_CHARGE_POP,core,4,0.05,0.05,0.05,0);
                world.spawnParticle(Particle.DUST,core,2,0.04,0.04,0.04,0,dustP);
                double spiralR=0.42; double spiralSA=d*5.0;
                for (int arm=0;arm<4;arm++) { double angle=spiralSA+arm*(Math.PI/2.0);
                    Location sp=core.clone().add(fp1.clone().multiply(Math.cos(angle)*spiralR)
                            .add(fp2.clone().multiply(Math.sin(angle)*spiralR)));
                    world.spawnParticle(Particle.ELECTRIC_SPARK,sp,1,0.02,0.02,0.02,0); }
                if (fs%5==0) world.playSound(core,Sound.ENTITY_WARDEN_HEARTBEAT,0.3f,1.4f+(float)(d/range)*0.4f);
                for (Entity en:world.getNearbyEntities(core,hitRad,hitRad,hitRad)) {
                    if (en.equals(p)) continue;
                    if (en instanceof Warden w&&w.hasMetadata("wardenMorphOwner")) continue;
                    if (!(en instanceof LivingEntity le)) continue;
                    if (alreadyHit.contains(le.getUniqueId())) continue; alreadyHit.add(le.getUniqueId());
                    le.setNoDamageTicks(0); le.setHealth(Math.max(0,le.getHealth()-damage));
                    le.playEffect(EntityEffect.HURT);
                    Vector kb=le.getLocation().toVector().subtract(p.getLocation().toVector());
                    if (kb.lengthSquared()<0.01) kb=dir.clone(); kb=kb.normalize().multiply(knockback); kb.setY(Math.max(kb.getY(),0.6));
                    le.setVelocity(kb);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,80,0,false,false,true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,80,2,false,false,true));
                    world.spawnParticle(Particle.SCULK_SOUL,le.getLocation().add(0,1,0),60,0.8,0.8,0.8,0.10);
                    world.spawnParticle(Particle.SCULK_CHARGE_POP,le.getLocation().add(0,1,0),80,0.7,0.7,0.7,0.12);
                    world.playSound(le.getLocation(),Sound.ENTITY_WARDEN_SONIC_BOOM,2f,1.3f);
                    if (le instanceof Player tp) tp.sendMessage(leg("&5&lSonic Beam &7— You were struck! &8(Armour bypassed)"));
                }
            },(long)(s/5)+1); }
        p.sendMessage(leg("&5&lSonic Beam &7— Fired! &8(One use per morph)"));
        p.sendActionBar(leg("&5Sonic Beam &7— Used for this morph!"));
    }

    // ── WARDEN (unchanged — compact) ──────────────────────────────────────

    private void abilityWarden(Player p, int num, boolean start) {
        if (num==1) { if (start) {
            UUID uuid=p.getUniqueId(); Location center=p.getLocation().clone(); World world=center.getWorld();
            int cageR=plugin.getConfig().getInt("spheres.warden.ability1.domain-radius",8);
            int cageH=plugin.getConfig().getInt("spheres.warden.ability1.cage-height",5);
            int pulseInt=plugin.getConfig().getInt("spheres.warden.ability1.pulse-interval",40);
            int slowLvl=plugin.getConfig().getInt("spheres.warden.ability1.slowness-level",2);
            int darkDur=plugin.getConfig().getInt("spheres.warden.ability1.darkness-duration",60);
            wardenDomainActive.add(uuid); p.sendMessage(leg("&5&lWarden Domain &7— Cage rising!"));
            Map<Location,Material> origBlocks=new HashMap<>(); List<Location> cageBlocks=new ArrayList<>();
            for (int r=0;r<=cageR;r++) { final int fr=r;
                Bukkit.getScheduler().runTaskLater(plugin,()->{if (!wardenDomainActive.contains(uuid)) return;
                    for (double angle=0;angle<360;angle+=2) { double rad=Math.toRadians(angle);
                        int bx=(int)Math.round(center.getX()+fr*Math.cos(rad));
                        int bz=(int)Math.round(center.getZ()+fr*Math.sin(rad));
                        Location bl=new Location(world,bx,center.getBlockY()-1,bz); Block block=bl.getBlock();
                        origBlocks.putIfAbsent(bl.clone(),block.getType()); block.setType(Material.BEDROCK);
                        block.setMetadata("wardenDomain",new FixedMetadataValue(plugin,uuid.toString()));
                        cageBlocks.add(bl.clone()); }},r); }
            long wallStart=cageR+3L;
            for (int y=0;y<cageH;y++) { final int fy=y;
                Bukkit.getScheduler().runTaskLater(plugin,()->{if (!wardenDomainActive.contains(uuid)) return;
                    for (double angle=0;angle<360;angle+=2) { double rad=Math.toRadians(angle);
                        int bx=(int)Math.round(center.getX()+cageR*Math.cos(rad));
                        int bz=(int)Math.round(center.getZ()+cageR*Math.sin(rad));
                        Location bl=new Location(world,bx,center.getBlockY()+fy,bz); Block block=bl.getBlock();
                        origBlocks.putIfAbsent(bl.clone(),block.getType()); block.setType(Material.BEDROCK);
                        block.setMetadata("wardenDomain",new FixedMetadataValue(plugin,uuid.toString()));
                        cageBlocks.add(bl.clone()); }},wallStart+fy*2L); }
            long ceilStart=wallStart+cageH*2L+2L;
            for (int r=cageR;r>=0;r--) { final int fr=r;
                Bukkit.getScheduler().runTaskLater(plugin,()->{if (!wardenDomainActive.contains(uuid)) return;
                    for (double angle=0;angle<360;angle+=2) { double rad=Math.toRadians(angle);
                        int bx=(int)Math.round(center.getX()+fr*Math.cos(rad));
                        int bz=(int)Math.round(center.getZ()+fr*Math.sin(rad));
                        Location bl=new Location(world,bx,center.getBlockY()+cageH,bz); Block block=bl.getBlock();
                        origBlocks.putIfAbsent(bl.clone(),block.getType()); block.setType(Material.BEDROCK);
                        block.setMetadata("wardenDomain",new FixedMetadataValue(plugin,uuid.toString()));
                        cageBlocks.add(bl.clone()); }},ceilStart+(long)(cageR-fr)); }
            long totalDelay=ceilStart+cageR+5L;
            Bukkit.getScheduler().runTaskLater(plugin,()->{if (!wardenDomainActive.contains(uuid)) return;
                wardenDomainBlocks.put(uuid,cageBlocks); wardenDomainOrigBlocks.put(uuid,origBlocks);
                world.playSound(center,Sound.ENTITY_WARDEN_AMBIENT,2f,0.5f);},totalDelay);
            BukkitTask domTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if (!p.isOnline()||!wardenDomainActive.contains(uuid)) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,pulseInt+10,1,false,false,true));
                for (Entity en:world.getNearbyEntities(center,cageR,cageH,cageR)) {
                    if (en.equals(p)||!(en instanceof LivingEntity le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,pulseInt+10,slowLvl,false,false,true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,pulseInt+5,0,false,false,true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,darkDur,0,false,false,true)); }
            },totalDelay+1,pulseInt);
            cancelTaskByUUID(wardenDomainTasks,uuid); wardenDomainTasks.put(uuid,domTask);
        } else { UUID uuid=p.getUniqueId(); wardenDomainActive.remove(uuid); cancelTaskByUUID(wardenDomainTasks,uuid);
            List<Location> domBlocks=wardenDomainBlocks.remove(uuid);
            Map<Location,Material> origBlocks=wardenDomainOrigBlocks.remove(uuid);
            if (domBlocks!=null) { domBlocks.sort((a,b)->b.getBlockY()-a.getBlockY());
                int maxY=domBlocks.stream().mapToInt(Location::getBlockY).max().orElse(0);
                for (Location bl:domBlocks) { long delay=(long)((maxY-bl.getBlockY())*1.5);
                    Bukkit.getScheduler().runTaskLater(plugin,()->{bl.getBlock().removeMetadata("wardenDomain",plugin);
                        Material orig=origBlocks!=null?origBlocks.get(bl):null;
                        bl.getBlock().setType(orig!=null?orig:Material.AIR);},delay); } }
            p.removePotionEffect(PotionEffectType.STRENGTH);
            p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WARDEN_DIG,1.5f,0.8f);
            p.sendMessage(leg("&5&lWarden Domain &7— Cage dissolved.")); } }
        if (num==2) { if (start) { UUID uuid=p.getUniqueId(); World world=p.getWorld();
            int dur=cfgI("warden",2,"duration");
            int resLvl=plugin.getConfig().getInt("spheres.warden.ability2.resistance-level",1);
            int strLvl=plugin.getConfig().getInt("spheres.warden.ability2.strength-level",0);
            double tRad=plugin.getConfig().getDouble("spheres.warden.ability2.tremor-radius",20.0);
            int tInt=plugin.getConfig().getInt("spheres.warden.ability2.tremor-interval",40);
            AttributeInstance mh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (mh!=null) { wardenOrigMaxHp.put(uuid,mh.getValue());
                new ArrayList<>(mh.getModifiers()).forEach(mh::removeModifier); mh.setBaseValue(40.0); p.setHealth(40.0); }
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,dur*20,resLvl,false,false,true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,dur*20,strLvl,false,false,true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,dur*20,0,false,false,false));
            hideArmorFromAll(p); shadowArmorHidden.add(uuid); morphBoomUsed.remove(uuid);
            wardenMorphActive.add(uuid);
            AttributeInstance scale=p.getAttribute(Attribute.GENERIC_SCALE); if (scale!=null) scale.setBaseValue(1.5);
            ArmorStand nameTag=(ArmorStand)p.getWorld().spawnEntity(p.getLocation().add(0,2.8,0),EntityType.ARMOR_STAND);
            nameTag.setVisible(false); nameTag.setGravity(false); nameTag.setInvulnerable(true); nameTag.setSmall(true);
            nameTag.setCustomName("§5§lWarden Transform"); nameTag.setCustomNameVisible(true); nameTag.setMarker(true);
            wardenMorphNameTags.put(uuid,nameTag);
            BukkitTask tagTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if (!p.isOnline()||!wardenMorphActive.contains(uuid)) return;
                ArmorStand t=wardenMorphNameTags.get(uuid); if (t==null||t.isDead()) return;
                t.teleport(p.getLocation().add(0,2.8,0));},0L,1L);
            wardenMorphTagTasks.put(uuid,tagTask);
            BukkitTask tremorTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if (!p.isOnline()||!wardenMorphActive.contains(uuid)) return;
                List<LivingEntity> det=new ArrayList<>();
                for (Entity en:world.getNearbyEntities(p.getLocation(),tRad,tRad,tRad)) {
                    if (en.equals(p)) continue; if (en instanceof Warden ww&&ww.hasMetadata("wardenMorphOwner")) continue;
                    if (!(en instanceof LivingEntity le)) continue; det.add(le);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,tInt+5,0,false,false,true)); }
                if (!det.isEmpty()) { p.sendActionBar(leg("&5&l⚠ TREMOR &7— &d"+det.size()+" &7entit"+(det.size()==1?"y":"ies")+" detected!"));
                    world.playSound(p.getLocation(),Sound.ENTITY_WARDEN_HEARTBEAT,0.6f,1.3f); }
            },0L,tInt);
            cancelTaskByUUID(wardenTremorTasks,uuid); wardenTremorTasks.put(uuid,tremorTask);
            world.playSound(p.getLocation(),Sound.ENTITY_WARDEN_EMERGE,2f,1.0f);
            world.spawnParticle(Particle.SCULK_SOUL,p.getLocation(),80,2.5,2.5,2.5,0.05);
            p.sendMessage(leg("&5&lWarden Morph &7— You have become the Warden!"));
            p.sendMessage(leg("&8Shift + Left Click &7→ &5Sonic Beam &8(one use this morph)"));
        } else { UUID uuid=p.getUniqueId(); wardenMorphActive.remove(uuid); morphBoomUsed.remove(uuid);
            cancelTaskByUUID(wardenMorphFollowTasks,uuid); cancelTaskByUUID(wardenTremorTasks,uuid);
            wardenMorphEntities.remove(uuid);
            AttributeInstance scale=p.getAttribute(Attribute.GENERIC_SCALE); if (scale!=null) scale.setBaseValue(1.0);
            ArmorStand nameTag=wardenMorphNameTags.remove(uuid);
            if (nameTag!=null&&!nameTag.isDead()) nameTag.remove(); cancelTaskByUUID(wardenMorphTagTasks,uuid);
            AttributeInstance mh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (mh!=null) { double orig=wardenOrigMaxHp.getOrDefault(uuid,20.0); mh.setBaseValue(orig);
                if (p.getHealth()>orig) p.setHealth(orig); } wardenOrigMaxHp.remove(uuid);
            shadowArmorHidden.remove(uuid); showArmorToAll(p);
            p.removePotionEffect(PotionEffectType.INVISIBILITY); p.removePotionEffect(PotionEffectType.RESISTANCE);
            p.removePotionEffect(PotionEffectType.STRENGTH); p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WARDEN_DIG,1.5f,0.8f);
            p.sendMessage(leg("&5&lWarden Morph &7— Reverted to human form.")); } }
    }

    // ── SHADOW (unchanged) ────────────────────────────────────────────────

    private void abilityShadow(Player p, int num, boolean start) {
        if (num==1) { if (start) { p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                cfgI("shadow",1,"duration")*20,0,false,false,false));
            hideArmorFromAll(p); shadowArmorHidden.add(p.getUniqueId());
            p.sendMessage(leg("&8&lShadow Strike &7— You vanish."));
        } else { p.removePotionEffect(PotionEffectType.INVISIBILITY);
            shadowArmorHidden.remove(p.getUniqueId()); showArmorToAll(p);
            p.sendMessage(leg("&8&lShadow Strike &7— You emerge.")); } }
        if (num==2) { if (start) { Location center=p.getLocation().clone(); World world=center.getWorld();
            int radius=plugin.getConfig().getInt("spheres.shadow.ability2.zone-radius",10);
            world.spawnParticle(Particle.LARGE_SMOKE,center,200,radius*0.5,2,radius*0.5,0.05);
            world.playSound(center,Sound.AMBIENT_CAVE,1.5f,0.5f);
            BukkitTask zt=Bukkit.getScheduler().runTaskTimer(plugin,()->{if (!p.isOnline()) return;
                for (Entity en:world.getNearbyEntities(center,radius,radius,radius)) {
                    if (en.equals(p)||!(en instanceof Player tp)) continue;
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,25,0,false,false,true));
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,25,1,false,false,true));
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,25,2,false,false,true));
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,25,0,false,false,true)); }
            },0L,20L);
            cancelTaskByUUID(shadowZoneTasks,p.getUniqueId()); shadowZoneTasks.put(p.getUniqueId(),zt);
            p.sendMessage(leg("&8&lDark Zone &7— Shadows consume the area."));
        } else { cancelTaskByUUID(shadowZoneTasks,p.getUniqueId());
            p.sendMessage(leg("&8&lDark Zone &7— Shadows recede.")); } }
    }

    // ── FROST (unchanged) ─────────────────────────────────────────────────

    private void abilityFrost(Player p, int num, boolean start) {
        if (num==1) { if (start) { frostSnowballActive.add(p.getUniqueId());
            p.setMetadata("sphereFrostBomb",new FixedMetadataValue(plugin,true));
            p.getInventory().remove(Material.SNOWBALL);
            p.getInventory().addItem(buildFrostBombSnowball());
            p.sendMessage(leg("&f&lFrost Bomb &7— &fSpecial Frost Bomb &7snowball given!"));
            p.sendMessage(leg("&7Throw it to create icebergs — unlimited while active."));
        } else { frostSnowballActive.remove(p.getUniqueId());
            p.removeMetadata("sphereFrostBomb",plugin); removeFrostBombSnowballs(p);
            p.sendMessage(leg("&f&lFrost Bomb &7— Ended. Snowball removed.")); } }
        if (num==2) { if (start) { p.setMetadata("sphereFrostTrail",new FixedMetadataValue(plugin,true));
            p.setMetadata("sphereFrostPrison",new FixedMetadataValue(plugin,true));
            p.sendMessage(leg("&f&lIce Trail &7— Packed ice forms beneath your feet. Walk on air!"));
        } else { p.removeMetadata("sphereFrostTrail",plugin); p.removeMetadata("sphereFrostPrison",plugin);
            List<Location> trail=iceTrailBlocks.remove(p.getUniqueId());
            if (trail!=null) trail.forEach(loc->{if (loc.getBlock().getType()==Material.PACKED_ICE) loc.getBlock().setType(Material.AIR);}); } }
    }

    // ── ProtocolLib — Hide / Show Armor ───────────────────────────────────

    private void hideArmorFromAll(Player target) {
        ProtocolManager pm=ProtocolLibrary.getProtocolManager();
        for (Player obs:Bukkit.getOnlinePlayers()) { if (obs.equals(target)) continue;
            try { List<Pair<EnumWrappers.ItemSlot,ItemStack>> pairs=new ArrayList<>();
                for (EnumWrappers.ItemSlot slot:new EnumWrappers.ItemSlot[]{
                        EnumWrappers.ItemSlot.HEAD,EnumWrappers.ItemSlot.CHEST,
                        EnumWrappers.ItemSlot.LEGS,EnumWrappers.ItemSlot.FEET})
                    pairs.add(new Pair<>(slot,new ItemStack(Material.AIR)));
                PacketContainer pkt=pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                pkt.getIntegers().write(0,target.getEntityId());
                pkt.getSlotStackPairLists().write(0,pairs);
                pm.sendServerPacket(obs,pkt); } catch (Exception ignored) {} }
    }
    private void showArmorToAll(Player target) {
        ProtocolManager pm=ProtocolLibrary.getProtocolManager();
        for (Player obs:Bukkit.getOnlinePlayers()) { if (obs.equals(target)) continue;
            try { List<Pair<EnumWrappers.ItemSlot,ItemStack>> pairs=List.of(
                    new Pair<>(EnumWrappers.ItemSlot.HEAD,target.getInventory().getHelmet()),
                    new Pair<>(EnumWrappers.ItemSlot.CHEST,target.getInventory().getChestplate()),
                    new Pair<>(EnumWrappers.ItemSlot.LEGS,target.getInventory().getLeggings()),
                    new Pair<>(EnumWrappers.ItemSlot.FEET,target.getInventory().getBoots()));
                PacketContainer pkt=pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                pkt.getIntegers().write(0,target.getEntityId());
                pkt.getSlotStackPairLists().write(0,pairs);
                pm.sendServerPacket(obs,pkt); } catch (Exception ignored) {} }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EVENTS
    // ═════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {

        // ── Water Beam trident hit (armor bypass) ─────────────────────────
        if (e.getDamager() instanceof Trident trident
                && trident.hasMetadata("waterBeam")
                && e.getEntity() instanceof LivingEntity victim) {
            if (!(trident.getShooter() instanceof Player shooter)) return;
            e.setCancelled(true);
            double dmg = plugin.getConfig().getDouble("spheres.water.ability1.beam-damage", 10.0);
            victim.setNoDamageTicks(0);
            victim.setHealth(Math.max(0, victim.getHealth() - dmg));
            victim.playEffect(EntityEffect.HURT);
            // Drowning
            double dc = plugin.getConfig().getDouble("spheres.water.ability1.drown-chance", 0.40);
            int dd = plugin.getConfig().getInt("spheres.water.ability1.drown-duration", 5);
            if (Math.random() < dc) {
                applyDrowning(victim, dd);
                if (victim instanceof Player tp)
                    tp.sendMessage(leg("&9&lWater Beam &7— You are &9drowning&7!"));
            }
            // Impact effects
            World w = victim.getWorld();
            w.spawnParticle(Particle.SPLASH, victim.getLocation().add(0,1,0), 60, 0.8,0.8,0.8, 0.3);
            w.spawnParticle(Particle.DRIPPING_WATER, victim.getLocation().add(0,1,0), 30, 0.5,0.5,0.5, 0.1);
            w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.8f);
            w.playSound(victim.getLocation(), Sound.ENTITY_DROWNED_HURT, 1.5f, 0.7f);
            trident.remove();
            return;
        }

        // ── Warden morph owner protection ─────────────────────────────────
        if (e.getDamager() instanceof Player attacker
                && e.getEntity() instanceof Warden wv
                && wv.hasMetadata("wardenMorphOwner")) {
            if (attacker.getUniqueId().toString().equals(
                    wv.getMetadata("wardenMorphOwner").get(0).asString())) {
                e.setCancelled(true); return; }
        }

        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        UUID uuid = p.getUniqueId();
        if (victim instanceof Warden wv && wv.hasMetadata("wardenMorphOwner")) return;

        ItemStack off = p.getInventory().getItemInOffHand();
        if (!isSphere(off)) return;
        String sphereType = getSphereType(off); if (sphereType == null) return;

        switch (sphereType) {
            case "fire" -> {
                double ic = plugin.getConfig().getDouble("spheres.fire.passive.ignite-chance", 0.4);
                if (Math.random() < ic) victim.setFireTicks(
                        plugin.getConfig().getInt("spheres.fire.passive.ignite-ticks", 80));
            }
            case "volt" -> {
                boolean raining = p.getWorld().hasStorm(); int smiteEvery = raining ? 5 : 10;
                double smiteDmg = plugin.getConfig().getDouble("spheres.volt.passive.smite-damage", 8.0);
                if (incrementHit(p, "volt") % smiteEvery == 0) {
                    p.getWorld().strikeLightningEffect(victim.getLocation()); victim.damage(smiteDmg, p); }
            }
            case "water" -> {
                // Passive drowning (10%)
                double passiveDC = plugin.getConfig().getDouble("spheres.water.passive.drown-chance", 0.10);
                int passiveDD    = plugin.getConfig().getInt("spheres.water.passive.drown-duration", 3);
                if (Math.random() < passiveDC) {
                    applyDrowning(victim, passiveDD);
                    p.getWorld().spawnParticle(Particle.BUBBLE_POP,
                            victim.getLocation().add(0,1,0), 15, 0.4,0.4,0.4, 0.05);
                }
                // Ability 1 enhanced drowning (40%)
                if (p.hasMetadata("sphereWaterBeam")) {
                    double a1DC = plugin.getConfig().getDouble("spheres.water.ability1.drown-chance", 0.40);
                    int a1DD    = plugin.getConfig().getInt("spheres.water.ability1.drown-duration", 5);
                    if (Math.random() < a1DC) {
                        applyDrowning(victim, a1DD);
                        if (victim instanceof Player tp)
                            tp.sendMessage(leg("&9&lWater &7— You are &9drowning&7!"));
                    }
                }
                // Ability 2 Aqua Form — Poison + Blindness
                if (p.hasMetadata("sphereWaterAqua")) {
                    int poisonD = plugin.getConfig().getInt("spheres.water.ability2.poison-duration", 60);
                    int blindD  = plugin.getConfig().getInt("spheres.water.ability2.blindness-duration", 60);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonD, 0, false, false, true));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindD, 0, false, false, true));
                }
            }
            case "shadow" -> {
                if (activeMap.containsKey(abilityKey(uuid, "shadow", 1))) {
                    int sHitEvery = plugin.getConfig().getInt("spheres.shadow.ability1.blind-every", 10);
                    if (incrementHit(p, "shadow") % sHitEvery == 0 && victim instanceof Player tp) {
                        tp.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                                plugin.getConfig().getInt("spheres.shadow.ability1.blind-duration", 100), 0, false, false, true));
                        tp.sendMessage(leg("&8&lShadow &7— Blinded!")); } }
            }
            case "frost" -> {
                double fc = plugin.getConfig().getDouble("spheres.frost.passive.freeze-chance", 0.30);
                if (Math.random() < fc) { int ft = plugin.getConfig().getInt("spheres.frost.passive.freeze-ticks", 100);
                    victim.setFreezeTicks(ft);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ft, 255, false, false, true));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ft, -10, false, false, false));
                    victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0,1,0), 20, 0.4,0.5,0.4, 0.04); }
                if (p.hasMetadata("sphereFrostPrison") && victim instanceof Player tp) {
                    double pc = plugin.getConfig().getDouble("spheres.frost.ability2.prison-chance", 0.5);
                    if (Math.random() < pc && !icePrisonBlocks.containsKey(tp.getUniqueId()))
                        createIcePrison(p, tp); }
            }
        }
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!isSphere(off) || !"wind".equals(getSphereType(off))) return;
        if (windSpeedLevel.getOrDefault(p.getUniqueId(), 1) > 1) {
            windSpeedLevel.put(p.getUniqueId(), 1); setWindSpeedAttribute(p, 0);
            p.sendMessage(leg("&b&lWind &7— Damage! Speed reset to I.")); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p=e.getPlayer(); Block block=e.getBlock(); UUID uuid=p.getUniqueId();
        if (block.hasMetadata("wardenDomain")) { e.setCancelled(true);
            p.sendActionBar(leg("&5This block is protected by a &dWarden Domain&5!")); return; }
        if (block.hasMetadata("frostPrison")) { e.setCancelled(true);
            p.sendMessage(leg("&f&lFrost &7This ice prison cannot be broken!")); return; }
        boolean a1Active=activeMap.containsKey(abilityKey(uuid,"earth",1));
        boolean a2Active=p.hasMetadata("sphereEarthShatter");
        if ((a1Active||a2Active)&&p.isSneaking()&&ORE_TYPES.contains(block.getType())) veinMine(p,block,a2Active);
        if (a2Active&&!p.isSneaking()) { for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            if (x==0&&y==0&&z==0) continue; Block b=block.getRelative(x,y,z);
            if (b.getType().isAir()||!b.getType().isSolid()) continue;
            if (b.hasMetadata("frostPrison")||b.hasMetadata("wardenDomain")) continue;
            if (SMELT_MAP.containsKey(b.getType())) { Material sm=SMELT_MAP.get(b.getType());
                Location l=b.getLocation(); b.setType(Material.AIR); l.getWorld().dropItemNaturally(l,new ItemStack(sm));
            } else b.breakNaturally(p.getInventory().getItemInMainHand()); } }
        if (a2Active&&SMELT_MAP.containsKey(block.getType())) { e.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(),new ItemStack(SMELT_MAP.get(block.getType()))); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p=e.getPlayer(); if (!p.hasMetadata("sphereFrostTrail")) return;
        ItemStack off=p.getInventory().getItemInOffHand();
        if (!isSphere(off)||!"frost".equals(getSphereType(off))) { p.removeMetadata("sphereFrostTrail",plugin); return; }
        Location below=p.getLocation().clone().add(0,-1,0); Block ground=below.getBlock(); Material orig=ground.getType();
        if (orig==Material.BEDROCK||orig==Material.PACKED_ICE||orig==Material.ICE||orig==Material.WATER) return;
        boolean wasAir=orig.isAir(); if (!wasAir&&!orig.isSolid()) return;
        ground.setType(Material.PACKED_ICE); UUID uuid=p.getUniqueId();
        List<Location> trail=iceTrailBlocks.computeIfAbsent(uuid,k->new ArrayList<>()); Location bl=below.clone(); trail.add(bl);
        final Material restoreTo=wasAir?Material.AIR:orig;
        int meltTicks=plugin.getConfig().getInt("spheres.frost.ability2.ice-trail-melt-ticks",100);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if (ground.getType()==Material.PACKED_ICE) ground.setType(restoreTo); trail.remove(bl);},meltTicks);
        while (trail.size()>60) { Location old=trail.remove(0);
            if (old.getBlock().getType()==Material.PACKED_ICE) old.getBlock().setType(Material.AIR); }
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, below.clone().add(0.5,1.0,0.5), 3, 0.2,0.1,0.2, 0.02);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p=e.getPlayer(); UUID uuid=p.getUniqueId(); Action action=e.getAction();
        // Warden Sonic Beam
        if (p.isSneaking()&&(action==Action.LEFT_CLICK_AIR||action==Action.LEFT_CLICK_BLOCK)) {
            if (wardenMorphActive.contains(uuid)) { e.setCancelled(true);
                if (morphBoomUsed.contains(uuid)) { p.sendActionBar(leg("&5Sonic Beam &7— Already fired this morph!")); return; }
                fireWardenSonicBoom(p); return; } }
        if (action==Action.RIGHT_CLICK_AIR||action==Action.RIGHT_CLICK_BLOCK) {
            ItemStack off=p.getInventory().getItemInOffHand();
            if (isSphere(off)) {
                String type = getSphereType(off);
                // Water Beam — fire trident on right-click during ability 1
                if ("water".equals(type) && activeMap.containsKey(abilityKey(uuid,"water",1))
                        && !waterBeamUsed.contains(uuid)) {
                    fireWaterTridentBeam(p); return;
                }
                // Wind charges
                if ("wind".equals(type) && windCharges.containsKey(uuid)) {
                    int charges=windCharges.get(uuid);
                    if (charges<=0) { windCharges.remove(uuid); return; }
                    windCharges.put(uuid,charges-1);
                    Vector vel=p.getVelocity(); p.setVelocity(new Vector(vel.getX(),1.3,vel.getZ()));
                    p.setFallDistance(0);
                    p.getWorld().spawnParticle(Particle.CLOUD,p.getLocation(),12,0.3,0.1,0.3,0.1);
                    p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_JUMP,1f,1.2f);
                    p.sendActionBar(leg("&b⚡ Wind Charges: &b"+(charges-1)));
                    if (charges-1==0) { windCharges.remove(uuid); p.sendMessage(leg("&7All wind charges consumed.")); }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        UUID uuid=p.getUniqueId(); ItemStack off=p.getInventory().getItemInOffHand();
        if (!isSphere(off)) return; String type=getSphereType(off);
        if ("frost".equals(type)&&e.getEntity() instanceof Snowball&&frostSnowballActive.contains(uuid)) {
            Bukkit.getScheduler().runTaskLater(plugin,()->{if (!p.isOnline()||!frostSnowballActive.contains(uuid)) return;
                boolean hasOne=false; for (ItemStack inv:p.getInventory().getContents())
                    if (isFrostBombSnowball(inv)) { hasOne=true; break; }
                if (!hasOne) p.getInventory().addItem(buildFrostBombSnowball());},1L);
            Location hit=e.getEntity().getLocation(); World world=hit.getWorld();
            int iceR=plugin.getConfig().getInt("spheres.frost.ability1.iceberg-radius",3);
            List<Location> placed=new ArrayList<>();
            for (int x=-iceR;x<=iceR;x++) for (int y=0;y<=iceR;y++) for (int z=-iceR;z<=iceR;z++) {
                if (Math.sqrt(x*x+y*y+z*z)>iceR) continue; Location bl=hit.clone().add(x,y,z);
                if (!bl.getBlock().getType().isAir()) continue; bl.getBlock().setType(Material.PACKED_ICE); placed.add(bl.clone());
                for (Entity en:world.getNearbyEntities(bl,1,1,1)) { if (en.equals(p)||!(en instanceof LivingEntity le)) continue;
                    le.setFreezeTicks(120); le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,120,255,false,false,false)); } }
            world.spawnParticle(Particle.SNOWFLAKE,hit,50,iceR,iceR,iceR,0.1);
            world.playSound(hit,Sound.ENTITY_PLAYER_HURT_FREEZE,1.5f,0.7f);
            int meltTicks=plugin.getConfig().getInt("spheres.frost.ability1.iceberg-melt-ticks",200);
            Bukkit.getScheduler().runTaskLater(plugin,()->placed.forEach(bl2->{
                if (bl2.getBlock().getType()==Material.PACKED_ICE) bl2.getBlock().setType(Material.AIR);}),meltTicks); }
        if ("shadow".equals(type)&&e.getEntity() instanceof Arrow
                &&activeMap.containsKey(abilityKey(uuid,"shadow",1))) {
            double rc=plugin.getConfig().getDouble("spheres.shadow.ability1.reveal-chance",0.30);
            if (Math.random()<rc) { double rr=plugin.getConfig().getDouble("spheres.shadow.ability1.reveal-radius",15.0);
                int gd=plugin.getConfig().getInt("spheres.shadow.ability1.reveal-glow-ticks",80);
                Location hitLoc=e.getEntity().getLocation();
                for (Entity en:hitLoc.getWorld().getNearbyEntities(hitLoc,rr,rr,rr)) {
                    if (en.equals(p)||!(en instanceof Player tp)) continue;
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,gd,0,false,false,true));
                    p.sendMessage(leg("&8&lShadow Arrow &7— Revealed &7"+tp.getName()+"!")); } } }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent e) {
        Player p=e.getPlayer(); ItemStack off=p.getInventory().getItemInOffHand();
        if (!isSphere(off)||!"earth".equals(getSphereType(off))) return;
        e.setAmount(e.getAmount()*3);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent e) {
        Player p=e.getPlayer(); UUID uuid=p.getUniqueId(); Material mat=e.getItem().getType();
        if (earthBlessingActive.contains(uuid)&&mat!=Material.POTION&&mat!=Material.SPLASH_POTION&&mat!=Material.LINGERING_POTION)
            Bukkit.getScheduler().runTaskLater(plugin,()->{if (!p.isOnline()) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,400,0,false,false,true));},1L);
        if (p.hasMetadata("sphereEarthShatter")&&!earthGoldenAppleUsed.contains(uuid)&&mat==Material.GOLDEN_APPLE) {
            earthGoldenAppleUsed.add(uuid);
            Bukkit.getScheduler().runTaskLater(plugin,()->{if (!p.isOnline()) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,400,1,false,false,true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,6000,0,false,false,true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,6000,0,false,false,true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,2400,3,false,false,true));
                p.sendMessage(leg("&a&lEarth Shatter &7Golden Apple enchanted effects applied! (one-time)"));},1L); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isLockedSphere(p,e.getCurrentItem())||isLockedSphere(p,e.getCursor())) {
            e.setCancelled(true); p.sendActionBar(leg("&c⚠ &7This sphere is locked &8— active ability or cooldown!")); }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isLockedSphere(p,e.getOldCursor())) { e.setCancelled(true);
            p.sendActionBar(leg("&c⚠ &7This sphere is locked &8— active ability or cooldown!")); }
    }
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        Player p=e.getPlayer();
        if (isLockedSphere(p,e.getItemDrop().getItemStack())) { e.setCancelled(true);
            p.sendActionBar(leg("&c⚠ &7This sphere is locked &8— active ability or cooldown!")); }
    }

    @EventHandler public void onItemHeld(PlayerItemHeldEvent e) {
        Player p=e.getPlayer(); Bukkit.getScheduler().runTaskLater(plugin,()->{if (p.isOnline()) checkSphereSwap(p);},1L); }
    @EventHandler public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p=e.getPlayer(); Bukkit.getScheduler().runTaskLater(plugin,()->{if (p.isOnline()) checkSphereSwap(p);},1L); }
    private void checkSphereSwap(Player p) {
        ItemStack off=p.getInventory().getItemInOffHand();
        if (isSphere(off)) schedulePassiveLoop(p,getSphereType(off)); else clearPassives(p); }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p=e.getPlayer(); loadPlayerCooldowns(p);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if (!p.isOnline()) return;
            ItemStack off=p.getInventory().getItemInOffHand();
            if (isSphere(off)) schedulePassiveLoop(p,getSphereType(off));},10L); }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p=e.getPlayer(); savePlayerCooldowns(p); clearPassives(p); clearAllAbilities(p); }

    // ── Passive Loop ──────────────────────────────────────────────────────

    private void schedulePassiveLoop(Player p, String type) {
        UUID uuid=p.getUniqueId(); cancelTaskByUUID(passiveTasks,uuid);
        resetFireLava(p); resetWindSpeed(p);
        passiveTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if (!p.isOnline()) { cancelTaskByUUID(passiveTasks,uuid); return; }
            ItemStack off=p.getInventory().getItemInOffHand();
            if (!isSphere(off)||!type.equals(getSphereType(off))) { clearPassives(p); return; }
            applyPassive(p,type);
        },0L,20L));
        switch (type) {
            case "fire" -> startFireLavaTask(p);
            case "wind" -> startWindSpeedRamp(p);
        }
        refreshActionBar(p,type);
    }

    // ── Apply Passive ─────────────────────────────────────────────────────

    private void applyPassive(Player p, String type) {
        switch (type) {
            case "fire" -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,40,0,false,false,false));
            case "wind" -> { int level=windSpeedLevel.getOrDefault(p.getUniqueId(),1);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,level-1,false,false,true)); }
            case "volt" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,false,true));
            case "water" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,40,0,false,false,false));
                // Dolphin's Grace II (level 1 = II)
                int dgLevel = plugin.getConfig().getInt("spheres.water.passive.dolphins-grace-level", 1);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,40,dgLevel,false,false,false));
                // Remove Depth Strider
                removeDepthStrider(p);
                // Boat speed boost
                if (p.isInsideVehicle() && p.getVehicle() instanceof Boat boat) {
                    Vector vel = boat.getVelocity();
                    if (vel.lengthSquared() > 0.005) {
                        double boost = plugin.getConfig().getDouble("spheres.water.passive.boat-speed-boost", 0.15);
                        Vector addVel = vel.clone().normalize().multiply(boost);
                        boat.setVelocity(vel.add(new Vector(addVel.getX(), 0, addVel.getZ())));
                    }
                }
            }
            case "earth" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,40,
                        plugin.getConfig().getInt("spheres.earth.passive.haste-level",1),false,false,true));
                applyEarthEnchants(p.getInventory().getItemInMainHand());
                Material under=p.getLocation().clone().subtract(0,1,0).getBlock().getType();
                if (under==Material.GRASS_BLOCK||under==Material.DIRT||under==Material.COARSE_DIRT
                        ||under==Material.ROOTED_DIRT||under==Material.PODZOL)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,false,true));
            }
            case "warden" -> p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,60,0,false,false,false));
            case "shadow" -> { if (!activeMap.containsKey(abilityKey(p.getUniqueId(),"shadow",1)))
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,40,0,false,false,false)); }
            case "frost" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,false,true));
                Material below=p.getLocation().clone().add(0,-1,0).getBlock().getType();
                if (below==Material.ICE||below==Material.PACKED_ICE||below==Material.BLUE_ICE
                        ||below==Material.SNOW_BLOCK||below==Material.POWDER_SNOW)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,2,false,false,true));
            }
        }
    }

    // ── Clear Passives ────────────────────────────────────────────────────

    private void clearPassives(Player p) {
        UUID uuid=p.getUniqueId();
        cancelTaskByUUID(passiveTasks,uuid); cancelTaskByUUID(actionBarTasks,uuid);
        resetFireLava(p); resetWindSpeed(p);
        // Warden morph cleanup
        if (wardenMorphActive.contains(uuid)) { wardenMorphActive.remove(uuid); morphBoomUsed.remove(uuid);
            cancelTaskByUUID(wardenMorphFollowTasks,uuid); cancelTaskByUUID(wardenTremorTasks,uuid);
            wardenMorphEntities.remove(uuid);
            AttributeInstance scale=p.getAttribute(Attribute.GENERIC_SCALE); if (scale!=null) scale.setBaseValue(1.0);
            p.setCustomName(null); p.setCustomNameVisible(false);
            AttributeInstance mh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (mh!=null) { double orig=wardenOrigMaxHp.getOrDefault(uuid,20.0); mh.setBaseValue(orig);
                if (p.isOnline()&&p.getHealth()>orig) p.setHealth(orig); }
            wardenOrigMaxHp.remove(uuid);
            p.removePotionEffect(PotionEffectType.RESISTANCE); p.removePotionEffect(PotionEffectType.STRENGTH);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION); }
        // Warden domain cleanup
        if (wardenDomainActive.contains(uuid)) { wardenDomainActive.remove(uuid);
            cancelTaskByUUID(wardenDomainTasks,uuid);
            List<Location> domBlocks=wardenDomainBlocks.remove(uuid);
            Map<Location,Material> origBlocks=wardenDomainOrigBlocks.remove(uuid);
            if (domBlocks!=null) domBlocks.forEach(bl->{bl.getBlock().removeMetadata("wardenDomain",plugin);
                Material orig=origBlocks!=null?origBlocks.get(bl):null;
                bl.getBlock().setType(orig!=null?orig:Material.AIR);});
            p.removePotionEffect(PotionEffectType.STRENGTH); }
        if (shadowArmorHidden.remove(uuid)) showArmorToAll(p);
        // Frost cleanup
        List<Location> trail=iceTrailBlocks.remove(uuid);
        if (trail!=null) trail.forEach(bl->{if (bl.getBlock().getType()==Material.PACKED_ICE) bl.getBlock().setType(Material.AIR);});
        frostSnowballActive.remove(uuid); if (p.isOnline()) removeFrostBombSnowballs(p);
        // Earth cleanup
        earthBlessingActive.remove(uuid); earthDiscountActive.remove(uuid); earthGoldenAppleUsed.remove(uuid);
        AttributeInstance earthMh=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (earthMh!=null) { earthMh.removeModifier(EARTH_BLESS_HEALTH_KEY);
            if (p.isOnline()&&p.getHealth()>earthMh.getValue()) p.setHealth(earthMh.getValue()); }
        removeEarthEnchants(p.getInventory().getItemInMainHand());
        // Water cleanup
        waterBeamUsed.remove(uuid);
        cancelTaskByUUID(waterPullTasks, uuid);
        restoreDepthStrider(p);
        p.removeMetadata("sphereWaterBeam", plugin);
        p.removeMetadata("sphereWaterAqua", plugin);
        p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        // General cleanup
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        p.removePotionEffect(PotionEffectType.WATER_BREATHING);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.HASTE);
        p.sendActionBar(Component.empty());
    }

    // ── Clear All Abilities ───────────────────────────────────────────────

    private void clearAllAbilities(Player p) {
        UUID uuid=p.getUniqueId(); String ustr=uuid.toString();
        for (String key:new ArrayList<>(durationTasks.keySet()).stream().filter(k->k.startsWith(ustr)).toList()) {
            cancelTaskByKey(durationTasks,key); activeMap.remove(key); }
        for (String key:new ArrayList<>(cooldownTasks.keySet()).stream().filter(k->k.startsWith(ustr)).toList()) {
            cancelTaskByKey(cooldownTasks,key); cooldownMap.remove(key); }
        cancelTaskByUUID(fireBeamTasks,uuid); cancelTaskByUUID(shadowZoneTasks,uuid);
        cancelTaskByUUID(acidRainTasks,uuid); cancelTaskByUUID(thunderApocTasks,uuid);
        acidRainActive.remove(uuid); voltLightningImmune.remove(uuid);
        windCharges.remove(uuid); cancelTaskByUUID(windChargeTasks,uuid); morphBoomUsed.remove(uuid);
        earthBlessingActive.remove(uuid); earthDiscountActive.remove(uuid); earthGoldenAppleUsed.remove(uuid);
        waterBeamUsed.remove(uuid); cancelTaskByUUID(waterPullTasks, uuid);
        if (p.isOnline()) removeFrostBombSnowballs(p);
        for (UUID victim:new ArrayList<>(icePrisonBlocks.keySet())) releaseIcePrison(victim);
        hitCounters.entrySet().removeIf(en->en.getKey().startsWith(ustr));
        if (p.isOnline()) { p.removePotionEffect(PotionEffectType.SPEED);
            p.removeMetadata("voltThunderApoc",plugin); p.removeMetadata("sphereWaterAqua",plugin);
            p.removeMetadata("sphereWaterBeam",plugin); p.removeMetadata("sphereEarthShatter",plugin);
            p.removeMetadata("sphereFrostTrail",plugin); p.removeMetadata("sphereFrostPrison",plugin);
            p.removeMetadata("sphereFrostBomb",plugin); }
    }

    // ── Shutdown ───────────────────────────────────────────────────────────

    public void shutdown() {
        for (Player p:Bukkit.getOnlinePlayers()) { savePlayerCooldowns(p); clearPassives(p); clearAllAbilities(p); }
        wardenMorphEntities.clear();
        for (UUID victim:new ArrayList<>(icePrisonBlocks.keySet())) releaseIcePrison(victim);
        for (var entry:wardenDomainBlocks.entrySet()) {
            Map<Location,Material> orig=wardenDomainOrigBlocks.get(entry.getKey());
            if (entry.getValue()!=null) entry.getValue().forEach(bl->{
                bl.getBlock().removeMetadata("wardenDomain",plugin);
                Material o=orig!=null?orig.get(bl):null; bl.getBlock().setType(o!=null?o:Material.AIR); }); }
        wardenDomainBlocks.clear(); wardenDomainOrigBlocks.clear();
        // Clear all drowning effects
        for (var entry : new HashMap<>(drowningEffects).entrySet()) {
            if (entry.getValue()!=null&&!entry.getValue().isCancelled()) entry.getValue().cancel();
            Player victim = Bukkit.getPlayer(entry.getKey());
            if (victim!=null&&victim.isOnline()) victim.setRemainingAir(victim.getMaximumAir());
        }
        drowningEffects.clear();
    }
}
