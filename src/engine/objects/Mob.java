// • ▌ ▄ ·.  ▄▄▄·  ▄▄ • ▪   ▄▄· ▄▄▄▄·  ▄▄▄·  ▐▄▄▄  ▄▄▄ .
// ·██ ▐███▪▐█ ▀█ ▐█ ▀ ▪██ ▐█ ▌▪▐█ ▀█▪▐█ ▀█ •█▌ ▐█▐▌·
// ▐█ ▌▐▌▐█·▄█▀▀█ ▄█ ▀█▄▐█·██ ▄▄▐█▀▀█▄▄█▀▀█ ▐█▐ ▐▌▐▀▀▀
// ██ ██▌▐█▌▐█ ▪▐▌▐█▄▪▐█▐█▌▐███▌██▄▪▐█▐█ ▪▐▌██▐ █▌▐█▄▄▌
// ▀▀  █▪▀▀▀ ▀  ▀ ·▀▀▀▀ ▀▀▀·▀▀▀ ·▀▀▀▀  ▀  ▀ ▀▀  █▪ ▀▀▀
//      Magicbane Emulator Project © 2013 - 2022
//                www.magicbane.com


package engine.objects;

import ch.claude_martin.enumbitset.EnumBitSet;
import engine.Enum;
import engine.Enum.*;
import engine.InterestManagement.WorldGrid;
import engine.exception.SerializationException;
import engine.gameManager.*;
import engine.job.JobScheduler;
import engine.jobs.DeferredPowerJob;
import engine.jobs.UpgradeNPCJob;
import engine.math.Bounds;
import engine.math.Quaternion;
import engine.math.Vector3f;
import engine.math.Vector3fImmutable;
import engine.mobileAI.Threads.MobAIThread;
import engine.net.ByteBufferWriter;
import engine.net.Dispatch;
import engine.net.DispatchMessage;
import engine.net.client.msg.PetMsg;
import engine.net.client.msg.PlaceAssetMsg;
import engine.powers.EffectsBase;
import engine.powers.MobPowerEntry;
import engine.server.MBServerStatics;
import org.joda.time.DateTime;
import org.pmw.tinylog.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static engine.math.FastMath.acos;
import static engine.net.client.msg.ErrorPopupMsg.sendErrorPopup;

public class Mob extends AbstractIntelligenceAgent {

    private static final ReentrantReadWriteLock createLock = new ReentrantReadWriteLock();
    private static final ConcurrentHashMap<Integer, Mob> mobMapByDBID = new ConcurrentHashMap<>(MBServerStatics.CHM_INIT_CAP, MBServerStatics.CHM_LOAD, MBServerStatics.CHM_THREAD_LOW);
    // Variables NOT to be stored in db
    private static int staticID = 0;
    //mob specific
    public final ConcurrentHashMap<Integer, Boolean> playerAgroMap = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Mob, Integer> siegeMinionMap = new ConcurrentHashMap<>(MBServerStatics.CHM_INIT_CAP, MBServerStatics.CHM_LOAD, MBServerStatics.CHM_THREAD_LOW);
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    public long nextCastTime = 0;
    public long nextCallForHelp = 0;
    public ReentrantReadWriteLock minionLock = new ReentrantReadWriteLock();
    public boolean despawned = false;
    public Vector3fImmutable destination = Vector3fImmutable.ZERO;
    public Vector3fImmutable localLoc = Vector3fImmutable.ZERO;
    public LinkedHashMap<Integer, Integer> mobPowers = new LinkedHashMap<>();
    public MobBase mobBase;
    public int spawnTime;
    public Zone parentZone;
    public Building building;
    public boolean hasLoot = false;
    public boolean isPlayerGuard = false;
    public AbstractCharacter npcOwner;
    public long deathTime = 0;
    public int equipmentSetID = 0;
    public int runeSet = 0;
    public int bootySet = 0;
    public EnumBitSet<MonsterType> notEnemy;
    public EnumBitSet<Enum.MonsterType> enemy;
    public MobBehaviourType BehaviourType;
    public ArrayList<Vector3fImmutable> patrolPoints;
    public int lastPatrolPointIndex = 0;
    public long stopPatrolTime = 0;
    public City guardedCity;
    protected int dbID; //the database ID
    protected int loadID;
    protected float spawnRadius;
    //used by static mobs
    protected int parentZoneID;
    protected float statLat;
    protected float statLon;
    protected float statAlt;
    private int currentID;
    private int ownerUID = 0; //only used by pets
    private AbstractWorldObject fearedObject = null;
    private int buildingID;
    private boolean isSiege = false;
    private long lastAttackTime = 0;
    private int lastMobPowerToken = 0;
    private HashMap<Integer, MobEquipment> equip = null;
    private DeferredPowerJob weaponPower;
    private DateTime upgradeDateTime = null;
    private boolean lootSync = false;

    public CompanionType companionType = null;


    /**
     * No Id Constructor
     */
    public Mob(String firstName, String lastName, short statStrCurrent, short statDexCurrent, short statConCurrent, short statIntCurrent, short statSpiCurrent, short level, int exp, boolean sit, boolean walk, boolean combat, Vector3fImmutable bindLoc, Vector3fImmutable currentLoc, Vector3fImmutable faceDir, short healthCurrent, short manaCurrent, short stamCurrent, Guild guild, byte runningTrains, int npcType, boolean isMob, Zone parent, Building building, int contractID) {
        super(firstName, lastName, statStrCurrent, statDexCurrent, statConCurrent, statIntCurrent, statSpiCurrent, level, exp, sit, walk, combat, bindLoc, currentLoc, faceDir, healthCurrent, manaCurrent, stamCurrent, guild, runningTrains);

        this.dbID = MBServerStatics.NO_DB_ROW_ASSIGNED_YET;
        this.loadID = npcType;
        this.mobBase = MobBase.getMobBase(loadID);
        this.currentID = MBServerStatics.NO_DB_ROW_ASSIGNED_YET;
        this.parentZone = parent;
        this.parentZoneID = (parent != null) ? parent.getObjectUUID() : 0;
        this.building = building;

        if (building != null)
            this.buildingID = building.getObjectUUID();
        else
            this.buildingID = 0;

        if (contractID == 0)
            this.contract = null;
        else
            this.contract = DbManager.ContractQueries.GET_CONTRACT(contractID);
        if (building != null && building.getOwner() != null) {
            this.lastName = "the " + contract.getName();
        }
        clearStatic();
    }

    /**
     * Normal Constructor
     */
    public Mob(String firstName, String lastName, short statStrCurrent, short statDexCurrent, short statConCurrent, short statIntCurrent, short statSpiCurrent, short level, int exp, boolean sit, boolean walk, boolean combat, Vector3fImmutable bindLoc, Vector3fImmutable currentLoc, Vector3fImmutable faceDir, short healthCurrent, short manaCurrent, short stamCurrent, Guild guild, byte runningTrains, int npcType, boolean isMob, Zone parent, int newUUID, Building building, int contractID) {
        super(firstName, lastName, statStrCurrent, statDexCurrent, statConCurrent, statIntCurrent, statSpiCurrent, level, exp, sit, walk, combat, bindLoc, currentLoc, faceDir, healthCurrent, manaCurrent, stamCurrent, guild, runningTrains, newUUID);
        this.dbID = newUUID;
        this.loadID = npcType;

        if (contractID == 0)
            this.contract = null;
        else
            this.contract = DbManager.ContractQueries.GET_CONTRACT(contractID);

        this.mobBase = MobBase.getMobBase(loadID);
        this.parentZone = parent;
        this.parentZoneID = (parent != null) ? parent.getObjectUUID() : 0;
        this.building = building;
        initializeMob(false, false, false);
        clearStatic();
    }

    /**
     * Pet Constructor
     */
    public Mob(MobBase mobBase, Guild guild, Zone parent, short level, PlayerCharacter owner, int tableID) {
        super(mobBase.getFirstName(), "", (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, level, 0, false, true, false, owner.getLoc(), owner.getLoc(), owner.getFaceDir(), (short) mobBase.getHealthMax(), (short) 0, (short) 0, guild, (byte) 0, tableID);
        this.dbID = tableID;
        this.loadID = mobBase.getObjectUUID();
        this.mobBase = mobBase;
        this.parentZone = parent;
        this.parentZoneID = (parent != null) ? parent.getObjectUUID() : 0;
        this.ownerUID = owner.getObjectUUID();
        this.BehaviourType = Enum.MobBehaviourType.Pet1;
        initializeMob(true, false, false);
        clearStatic();
    }

    //SIEGE CONSTRUCTOR
    public Mob(MobBase mobBase, Guild guild, Zone parent, short level, Vector3fImmutable loc, int tableID, boolean isPlayerGuard) {
        super(mobBase.getFirstName(), "", (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, level, 0, false, true, false, loc, loc, Vector3fImmutable.ZERO, (short) mobBase.getHealthMax(), (short) 0, (short) 0, guild, (byte) 0, tableID);
        this.dbID = tableID;
        this.loadID = mobBase.getObjectUUID();
        this.mobBase = mobBase;
        this.parentZone = parent;
        this.parentZoneID = (parent != null) ? parent.getObjectUUID() : 0;
        this.ownerUID = 0;
        this.equip = new HashMap<>();
        initializeMob(false, true, isPlayerGuard);
        clearStatic();
    }

    /**
     * ResultSet Constructor
     */
    public Mob(ResultSet rs) throws SQLException {

        super(rs);

        try {
            this.dbID = rs.getInt(1);
            //this.state = STATE.Idle;
            this.loadID = rs.getInt("mob_mobbaseID");
            this.gridObjectType = GridObjectType.DYNAMIC;
            this.spawnRadius = rs.getFloat("mob_spawnRadius");
            this.spawnTime = rs.getInt("mob_spawnTime");
            this.parentZone = null;
            this.statLat = rs.getFloat("mob_spawnX");
            this.statAlt = rs.getFloat("mob_spawnY");
            this.statLon = rs.getFloat("mob_spawnZ");

            this.localLoc = new Vector3fImmutable(this.statLat, this.statAlt, this.statLon);

            this.parentZoneID = rs.getInt("parent");
            this.level = (short) rs.getInt("mob_level");
            int buildingID = rs.getInt("mob_buildingID");

            try {
                this.building = BuildingManager.getBuilding(buildingID);
            } catch (Exception e) {
                this.building = null;
                Logger.error(e.getMessage());
            }

            int contractID = rs.getInt("mob_contractID");

            if (contractID == 0)
                this.contract = null;
            else
                this.contract = DbManager.ContractQueries.GET_CONTRACT(contractID);

            if (this.contract != null && NPC.ISGuardCaptain(contract.getContractID())) {
                this.spawnTime = 60 * 15;
                this.isPlayerGuard = true;
            }

            int guildID = rs.getInt("mob_guildUID");

            if (this.building != null)
                this.guild = this.building.getGuild();
            else
                this.guild = Guild.getGuild(guildID);

            if (this.guild == null)
                this.guild = Guild.getErrantGuild();

            java.util.Date sqlDateTime;
            sqlDateTime = rs.getTimestamp("upgradeDate");

            if (sqlDateTime != null)
                upgradeDateTime = new DateTime(sqlDateTime);
            else
                upgradeDateTime = null;

            // Submit upgrade job if NPC is currently set to rank.

            if (this.upgradeDateTime != null)
                Mob.submitUpgradeJob(this);

            this.mobBase = MobBase.getMobBase(loadID);

            this.setObjectTypeMask(MBServerStatics.MASK_MOB | this.getTypeMasks());

            if (this.mobBase != null && this.spawnTime == 0)
                this.spawnTime = this.mobBase.getSpawnTime();

            this.bindLoc = new Vector3fImmutable(this.statLat, this.statAlt, this.statLon);

            this.runeSet = rs.getInt("runeSet");
            this.bootySet = rs.getInt("bootySet");

            this.notEnemy = EnumBitSet.asEnumBitSet(rs.getLong("notEnemy"), Enum.MonsterType.class);
            this.enemy = EnumBitSet.asEnumBitSet(rs.getLong("enemy"), Enum.MonsterType.class);
            this.firstName = rs.getString("mob_name");

            if (this.firstName.isEmpty())
                this.firstName = this.mobBase.getFirstName();

            if (this.contract != null) {
                this.equipmentSetID = this.contract.getEquipmentSet();
                this.lastName = this.getContract().getName();
            } else
                this.equipmentSetID = rs.getInt("equipmentSet");


            if (rs.getString("fsm").length() > 1)
                this.BehaviourType = MobBehaviourType.valueOf(rs.getString("fsm"));

        } catch (Exception e) {
            Logger.error(e + " " + this.dbID);
        }

        try {
            initializeMob(false, false, this.isPlayerGuard);
        } catch (Exception e) {
            Logger.error("Mobile:" + this.dbID + ": " + e);
        }

    }

    public static void serializeMobForClientMsgOtherPlayer(Mob mob, ByteBufferWriter writer) throws SerializationException {
        Mob.serializeForClientMsgOtherPlayer(mob, writer);
    }

    public static void serializeForClientMsgOtherPlayer(Mob mob, ByteBufferWriter writer) throws SerializationException {
        writer.putInt(0);
        writer.putInt(0);

        int tid = (mob.mobBase != null) ? mob.mobBase.getLoadID() : 0;
        if (mob.isPet()) {
            writer.putInt(2);
            writer.putInt(3);
            writer.putInt(0);
            writer.putInt(2522);
            writer.putInt(GameObjectType.NPCClassRune.ordinal());
            writer.putInt(mob.currentID);
        } else if (tid == 100570) { //kur'adar
            writer.putInt(3);
            Mob.serializeRune(mob, writer, 3, GameObjectType.NPCClassRuneTwo.ordinal(), 2518); //warrior class
            serializeRune(mob, writer, 5, GameObjectType.NPCClassRuneThree.ordinal(), 252621); //guard rune
        } else if (tid == 100962 || tid == 100965) { //Spydraxxx the Mighty, Denigo Tantric
            writer.putInt(2);
            serializeRune(mob, writer, 5, GameObjectType.NPCClassRuneTwo.ordinal(), 252621); //guard rune
        } else if (mob.contract != null || mob.isPlayerGuard) {
            writer.putInt(3);
            serializeRune(mob, writer, 3, GameObjectType.NPCClassRuneTwo.ordinal(), MobBase.GetClassType(mob.getMobBaseID())); //warrior class
            serializeRune(mob, writer, 5, GameObjectType.NPCClassRuneThree.ordinal(), 252621); //guard rune
        } else
            writer.putInt(1);

        //Generate Race Rune
        writer.putInt(1);
        writer.putInt(0);

        if (mob.mobBase != null)
            writer.putInt(mob.mobBase.getLoadID());
        else
            writer.putInt(mob.loadID);

        writer.putInt(mob.getObjectType().ordinal());
        writer.putInt(mob.currentID);

        //Send Stats
        writer.putInt(5);
        writer.putInt(0x8AC3C0E6); //Str
        writer.putInt(0);
        writer.putInt(0xACB82E33); //Dex
        writer.putInt(0);
        writer.putInt(0xB15DC77E); //Con
        writer.putInt(0);
        writer.putInt(0xE07B3336); //Int
        writer.putInt(0);
        writer.putInt(0xFF665EC3); //Spi
        writer.putInt(0);


        writer.putString(mob.firstName);
        writer.putString(mob.lastName);


        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);

        writer.put((byte) 0);
        writer.putInt(mob.getObjectType().ordinal());
        writer.putInt(mob.currentID);

        if (mob.mobBase != null) {
            writer.putFloat(mob.mobBase.getScale());
            writer.putFloat(mob.mobBase.getScale());
            writer.putFloat(mob.mobBase.getScale());
        } else {
            writer.putFloat(1.0f);
            writer.putFloat(1.0f);
            writer.putFloat(1.0f);
        }

        writer.putVector3f(mob.getLoc());

        //Rotation
        float radians = (float) Math.acos(mob.getRot().y) * 2;

        if (mob.building != null)
            if (mob.building.getBounds() != null && mob.building.getBounds().getQuaternion() != null)
                radians += (mob.building.getBounds().getQuaternion()).angleY;

        writer.putFloat(radians);

        //Inventory Stuff

        writer.putInt(0);

        // get a copy of the equipped items.

        if (mob.equip != null) {

            writer.putInt(mob.equip.size());

            for (MobEquipment me : mob.equip.values())
                MobEquipment.serializeForClientMsg(me, writer);
        } else
            writer.putInt(0);

        writer.putInt(mob.getRank());
        writer.putInt(mob.getLevel());
        writer.putInt(mob.getIsSittingAsInt()); //Standing
        writer.putInt(mob.getIsWalkingAsInt()); //Walking
        writer.putInt(mob.getIsCombatAsInt()); //Combat
        writer.putInt(2); //Unknown
        writer.putInt(1); //Unknown - Headlights?
        writer.putInt(0);

        if (mob.building != null && mob.region != null) {
            writer.putInt(mob.building.getObjectType().ordinal());
            writer.putInt(mob.building.getObjectUUID());
        } else {
            writer.putInt(0); //<-Building Object Type
            writer.putInt(0); //<-Building Object ID
        }

        writer.put((byte) 0);
        writer.put((byte) 0);
        writer.put((byte) 0);

        writer.putInt(0); // NPC menu options

        if (mob.contract != null && mob.npcOwner == null) {
            writer.put((byte) 1);
            writer.putLong(0);
            writer.putLong(0);

            if (mob.contract != null)
                writer.putInt(mob.contract.getIconID());
            else
                writer.putInt(0); //npc icon ID

        } else
            writer.put((byte) 0);

        if (mob.npcOwner != null) {
            writer.put((byte) 1);
            writer.putInt(GameObjectType.PlayerCharacter.ordinal());
            writer.putInt(131117009);
            writer.putInt(mob.npcOwner.getObjectType().ordinal());
            writer.putInt(mob.npcOwner.getObjectUUID());
            writer.putInt(8);
        } else
            writer.put((byte) 0);

        if (mob.isPet()) {

            writer.put((byte) 1);

            if (mob.getOwner() != null) {
                writer.putInt(mob.getOwner().getObjectType().ordinal());
                writer.putInt(mob.getOwner().getObjectUUID());
            } else {
                writer.putInt(0); //ownerType
                writer.putInt(0); //ownerID
            }
        } else
            writer.put((byte) 0);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);

        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);

        if (!mob.isAlive() && !mob.isPet() && !mob.isNecroPet() && !mob.isSiege && !mob.isPlayerGuard) {
            writer.putInt(0);
            writer.putInt(0);
        }

        writer.put((byte) 0);
        Guild._serializeForClientMsg(mob.getGuild(), writer);
        if (mob.mobBase != null && mob.mobBase.getObjectUUID() == 100570) {
            writer.putInt(2);
            writer.putInt(0x00008A2E);
            writer.putInt(0x1AB84003);
        } else if (mob.isSiege) {
            writer.putInt(1);
            writer.putInt(74620179);
        } else
            writer.putInt(0);

        writer.putInt(0); //0xB8400300
        writer.putInt(0);

        //TODO Guard
        writer.put((byte) 0);

        writer.putFloat(mob.healthMax);
        writer.putFloat(mob.health.get());

        //TODO Peace Zone
        writer.put((byte) 1); //0=show tags, 1=don't

        //DON't LOAD EFFECTS FOR DEAD MOBS.

        if (!mob.isAlive())
            writer.putInt(0);
        else {
            int indexPosition = writer.position();
            writer.putInt(0); //placeholder for item cnt
            int total = 0;

            for (Effect eff : mob.getEffects().values()) {
                if (eff.isStatic())
                    continue;
                if (!eff.serializeForLoad(writer))
                    continue;
                ++total;
            }

            writer.putIntAt(total, indexPosition);
        }

        // Effects
        writer.put((byte) 0);
    }

    private static void serializeRune(Mob mob, ByteBufferWriter writer, int type, int objectType, int runeID) {
        writer.putInt(type);
        writer.putInt(0);
        writer.putInt(runeID);
        writer.putInt(objectType);
        writer.putInt(mob.currentID);
    }

    public static Mob createMob(int loadID, Vector3fImmutable spawn, Guild guild, boolean isMob, Zone parent, Building building, int contractID, String pirateName, int level) {

        Mob mobWithoutID = new Mob(pirateName, "", (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 1, 0, false, false, false, spawn, spawn, Vector3fImmutable.ZERO, (short) 1, (short) 1, (short) 1, guild, (byte) 0, loadID, isMob, parent, building, contractID);

        if (mobWithoutID.mobBase == null)
            return null;

        mobWithoutID.level = (short) level;

        // Parent zone is required by dbhandler.  Can likely
        // refactor that out and just use the id.

        mobWithoutID.parentZone = parent;
        mobWithoutID.parentZoneID = parent.getObjectUUID();

        // NPC in a Building derives position from slot

        if (mobWithoutID.building != null)
            mobWithoutID.bindLoc = Vector3fImmutable.ZERO;

        Mob mob;

        try {
            mob = DbManager.MobQueries.ADD_MOB(mobWithoutID);
            mob.setObjectTypeMask(MBServerStatics.MASK_MOB | mob.getTypeMasks());

        } catch (Exception e) {
            Logger.error("SQLException:" + e.getMessage());
            mob = null;
        }

        return mob;
    }

    public static Mob createPet(int loadID, Guild guild, Zone parent, PlayerCharacter owner, short level) {
        MobBase mobBase = MobBase.getMobBase(loadID);
        Mob mob = null;

        if (mobBase == null || owner == null)
            return null;

        createLock.writeLock().lock();
        level += 20;

        try {
            mob = new Mob(mobBase, guild, parent, level, owner, 0);
            if (mob.mobBase == null)
                return null;
            mob.runAfterLoad();
            Vector3fImmutable loc = owner.getLoc();
            DbManager.addToCache(mob);
            mob.setPet(owner, true);
            mob.setWalkMode(false);

        } catch (Exception e) {
            Logger.error(e);
        } finally {
            createLock.writeLock().unlock();
        }
        parent.zoneMobSet.add(mob);
        mob.level = level;
        mob.healthMax = mob.getMobBase().getHealthMax() * (mob.level * 0.5f);
        mob.health.set(mob.healthMax);
        return mob;
    }

    public static Mob createCompanion(int loadID, Guild guild, Zone parent, PlayerCharacter owner, short level) {
        MobBase mobBase = MobBase.getMobBase(loadID);
        Mob mob = null;

        if (mobBase == null || owner == null)
            return null;

        createLock.writeLock().lock();
        level += 20;

        try {
            mob = new Mob(mobBase, guild, parent, level, owner, 0);
            if (mob.mobBase == null)
                return null;
            mob.runAfterLoad();
            Vector3fImmutable loc = owner.getLoc();
            DbManager.addToCache(mob);
            mob.setPet(owner, true);
            mob.setWalkMode(false);

        } catch (Exception e) {
            Logger.error(e);
        } finally {
            createLock.writeLock().unlock();
        }
        parent.zoneMobSet.add(mob);
        mob.level = level;
        mob.healthMax = mob.getMobBase().getHealthMax() * (mob.level * 0.5f);
        mob.health.set(mob.healthMax);
        mob.BehaviourType = MobBehaviourType.COMPANION;
        return mob;
    }

    public static Mob getMob(int id) {

        if (id == 0)
            return null;

        Mob mob = (Mob) DbManager.getFromCache(GameObjectType.Mob, id);
        if (mob != null)
            return mob;
        return DbManager.MobQueries.GET_MOB(id);
    }

    public static Mob getFromCache(int id) {


        return (Mob) DbManager.getFromCache(GameObjectType.Mob, id);
    }

    public static Mob getFromCacheDBID(int id) {
        if (Mob.mobMapByDBID.containsKey(id))
            return Mob.mobMapByDBID.get(id);
        return null;
    }

    private static float getModifiedAmount(CharacterSkill skill) {

        if (skill == null)
            return 0f;

        return skill.getModifiedAmount();
    }

    public static void HandleAssistedAggro(PlayerCharacter source, PlayerCharacter target) {

        HashSet<AbstractWorldObject> mobsInRange = WorldGrid.getObjectsInRangePartial(source, MobAIThread.AI_DROP_AGGRO_RANGE, MBServerStatics.MASK_MOB);

        for (AbstractWorldObject awo : mobsInRange) {
            Mob mob = (Mob) awo;

            //Mob is not attacking anyone, skip.
            if (mob.getCombatTarget() == null)
                continue;

            //Mob not attacking target's target, let's not be failmu and skip this target.
            if (mob.getCombatTarget() != target)
                continue;

            //target is mob's combat target, LETS GO.

            if (source.getHateValue() > target.getHateValue())
                mob.setCombatTarget(source);
        }
    }

    public static void submitUpgradeJob(Mob mob) {

        if (mob.getUpgradeDateTime() == null) {
            Logger.error("Failed to get Upgrade Date");
            return;
        }

        // Submit upgrade job for future date or current instant

        if (mob.getUpgradeDateTime().isAfter(DateTime.now()))
            JobScheduler.getInstance().scheduleJob(new UpgradeNPCJob(mob), mob.getUpgradeDateTime().getMillis());
        else
            JobScheduler.getInstance().scheduleJob(new UpgradeNPCJob(mob), 0);

    }

    public static int getUpgradeTime(Mob mob) {

        if (mob.getRank() < 7)
            return (mob.getRank() * 8);

        return 0;
    }

    public static int getUpgradeCost(Mob mob) {

        int upgradeCost;

        upgradeCost = Integer.MAX_VALUE;

        if (mob.getRank() < 7)
            return (mob.getRank() * 100650) + 21450;

        return upgradeCost;
    }

    public static void setUpgradeDateTime(Mob mob, DateTime upgradeDateTime) {

        if (!DbManager.MobQueries.updateUpgradeTime(mob, upgradeDateTime)) {
            Logger.error("Failed to set upgradeTime for building " + mob.currentID);
            return;
        }
        mob.upgradeDateTime = upgradeDateTime;
    }

    public static synchronized Mob createGuardMob(Mob guardCaptain, Guild guild, Zone parent, Vector3fImmutable loc, short level, String pirateName) {

        MobBase minionMobBase;
        Mob mob;
        int maxSlots;

        switch (guardCaptain.getRank()) {
            case 3:
                maxSlots = 2;
                break;
            case 4:
            case 5:
                maxSlots = 3;
                break;
            case 6:
                maxSlots = 4;
                break;
            case 7:
                maxSlots = 5;
                break;
            case 1:
            case 2:
            default:
                maxSlots = 1;

        }

        if (guardCaptain.siegeMinionMap.size() == maxSlots)
            return null;

        minionMobBase = guardCaptain.mobBase;

        if (minionMobBase == null)
            return null;

        mob = new Mob(minionMobBase, guild, parent, level, new Vector3fImmutable(1, 1, 1), 0, true);
        mob.setLevel(level);
        if (guardCaptain.equipmentSetID != 0)
            mob.equipmentSetID = guardCaptain.equipmentSetID;

        mob.runAfterLoad();
        mob.despawned = true;
        mob.despawn();

        //grab equipment and name from minionbase.

        if (guardCaptain.contract != null) {
            Enum.MinionType minionType = Enum.MinionType.ContractToMinionMap.get(guardCaptain.contract.getContractID());
            if (minionType != null) {
                String rank;

                if (guardCaptain.getRank() < 3)
                    rank = MBServerStatics.JUNIOR;
                else if (guardCaptain.getRank() < 6)
                    rank = "";
                else if (guardCaptain.getRank() == 6)
                    rank = MBServerStatics.VETERAN;
                else
                    rank = MBServerStatics.ELITE;

                mob.firstName = NPCManager.getPirateName(mob.getMobBaseID());
                mob.lastName = rank + " " + minionType.getRace() + " " + minionType.getName();
            }
        }

        mob.setObjectTypeMask(MBServerStatics.MASK_MOB | mob.getTypeMasks());

        mob.isPlayerGuard = true;

        DbManager.addToCache(mob);

        RuneBase guardRune = RuneBase.getRuneBase(252621);

        for (MobBaseEffects mbe : guardRune.getEffectsList()) {

            EffectsBase eb = PowersManager.getEffectByToken(mbe.getToken());

            if (eb == null) {
                Logger.info("EffectsBase Null for Token " + mbe.getToken());
                continue;
            }

            //check to upgrade effects if needed.
            if (mob.effects.containsKey(Integer.toString(eb.getUUID()))) {
                if (mbe.getReqLvl() > (int) mob.level) {
                    continue;
                }

                Effect eff = mob.effects.get(Integer.toString(eb.getUUID()));

                if (eff == null)
                    continue;

                //Current effect is a higher rank, dont apply.
                if (eff.getTrains() > mbe.getRank())
                    continue;

                //new effect is of a higher rank. remove old effect and apply new one.
                eff.cancelJob();
                mob.addEffectNoTimer(Integer.toString(eb.getUUID()), eb, mbe.getRank(), true);
            } else {

                if (mbe.getReqLvl() > (int) mob.level)
                    continue;

                mob.addEffectNoTimer(Integer.toString(eb.getUUID()), eb, mbe.getRank(), true);
            }
        }

        int slot = 0;
        slot += guardCaptain.siegeMinionMap.size() + 1;

        guardCaptain.siegeMinionMap.put(mob, slot);
        mob.deathTime = System.currentTimeMillis();
        mob.npcOwner = guardCaptain;
        mob.spawnTime = (int) (-2.500 * guardCaptain.building.getRank() + 22.5) * 60;
        mob.BehaviourType = Enum.MobBehaviourType.GuardMinion;
        mob.guardedCity = guardCaptain.guardedCity;
        mob.parentZone = parent;
        parent.zoneMobSet.add(mob);
        MovementManager.translocate(mob, guardCaptain.bindLoc, guardCaptain.region);
        mob.bindLoc = guardCaptain.bindLoc;
        return mob;
    }

    public static synchronized Mob createSiegeMob(NPC owner, int loadID, Guild guild, Zone parent, Vector3fImmutable loc, short level) {

        MobBase minionMobBase;
        Mob mob;

        if (owner.getSiegeMinionMap().size() == 3)
            return null;

        minionMobBase = MobBase.getMobBase(loadID);

        if (minionMobBase == null)
            return null;

        mob = new Mob(minionMobBase, guild, parent, level, new Vector3fImmutable(1, 1, 1), 0, false);
        //mob.runAfterLoad();
        mob.despawned = true;
        DbManager.addToCache(mob);

        mob.setObjectTypeMask(MBServerStatics.MASK_MOB | mob.getTypeMasks());

        //mob.setMob();
        mob.setSiege(true);

        int slot = 0;

        if (!owner.getSiegeMinionMap().containsValue(1))
            slot = 1;
        else if (!owner.getSiegeMinionMap().containsValue(2))
            slot = 2;

        owner.getSiegeMinionMap().put(mob, slot);

        mob.setNpcOwner(owner);
        mob.BehaviourType = MobBehaviourType.Pet1;
        mob.BehaviourType.canRoam = false;
        return mob;
    }

    private void clearStatic() {

        if (this.parentZone != null)
            this.parentZone.zoneMobSet.remove(this);

        this.parentZone = null;
        this.statLat = 0f;
        this.statLon = 0f;
        this.statAlt = 0f;
    }

    private void initializeMob(boolean isPet, boolean isSiege, boolean isGuard) {

        int slot;
        Vector3fImmutable slotLocation;
        Quaternion slotRotation;

        if (ConfigManager.serverType.equals(ServerType.LOGINSERVER))
            return;

        // Configure parent zone adding this NPC to the
        // zone collection

        this.parentZone = ZoneManager.getZoneByUUID(this.parentZoneID);
        this.parentZone.zoneMobSet.remove(this);
        this.parentZone.zoneMobSet.add(this);

        // Setup location for this Mobile

        this.bindLoc = new Vector3fImmutable(this.statLat, this.statAlt, this.statLon);
        this.bindLoc = this.parentZone.getLoc().add(this.bindLoc);
        this.loc = new Vector3fImmutable(bindLoc);
        this.endLoc = new Vector3fImmutable(bindLoc);

        // Handle Mobiles within buildings

        if (this.building != null) {

            if (this.contract == null) {

                // Mobiles inside buildings are offset from it not the zone

                this.bindLoc = new Vector3fImmutable(this.statLat, this.statAlt, this.statLon);
                this.bindLoc = this.building.getLoc().add(this.bindLoc);

            } else {

                // Get next available slot for this Mobile and then
                // add it to the building's hireling list

                slot = BuildingManager.getAvailableSlot(building);

                if (slot == -1)
                    Logger.error("No available slot for Mobile: " + this.getObjectUUID());

                building.getHirelings().put(this, slot);

                // Override bind and location for this contracted Mobile
                // derived from BuildingManager slot location data.

                slotLocation = BuildingManager.getSlotLocation(building, slot).getLocation();

                this.bindLoc = building.getLoc().add(slotLocation);

            }

            // Rotate slot position by the building rotation

            this.bindLoc = Vector3fImmutable.rotateAroundPoint(building.getLoc(), this.bindLoc, building.getBounds().getQuaternion().angleY);

            this.loc = new Vector3fImmutable(bindLoc);
            this.endLoc = new Vector3fImmutable(bindLoc);

            // Rotate mobile rotation by the building's rotation

            slotRotation = new Quaternion().fromAngles(0, acos(this.getRot().y) * 2, 0);
            slotRotation = slotRotation.mult(building.getBounds().getQuaternion());
            this.setRot(new Vector3f(0, slotRotation.y, 0));

            // Configure building region and floor/level for this Mobile

            this.region = BuildingManager.GetRegion(this.building, bindLoc.x, bindLoc.y, bindLoc.z);
        }

        if (this.mobBase != null) {
            this.gridObjectType = GridObjectType.DYNAMIC;
            this.healthMax = this.mobBase.getHealthMax();
            this.manaMax = 0;
            this.staminaMax = 0;
            this.setHealth(this.healthMax);
            this.mana.set(this.manaMax);
            this.stamina.set(this.staminaMax);

            if (isPet)
                this.setObjectTypeMask(MBServerStatics.MASK_PET | this.getTypeMasks());

            if (this.contract == null)
                this.level = (short) this.mobBase.getLevel();
        }

        //set bonuses
        this.bonuses = new PlayerBonuses(this);

        //TODO set these correctly later
        this.rangeHandOne = 8;
        this.rangeHandTwo = -1;
        this.minDamageHandOne = 0;
        this.maxDamageHandOne = 0;
        this.minDamageHandTwo = 1;
        this.maxDamageHandTwo = 4;
        this.atrHandOne = 300;
        this.defenseRating = (short) this.mobBase.getDefenseRating();
        this.isActive = true;

        this.charItemManager.load();

        //load AI for general mobs.
        if (this.contract != null && NPC.ISWallArcher(this.contract)) {
            this.BehaviourType = MobBehaviourType.GuardWallArcher;
            this.isPlayerGuard = true;
            this.spawnTime = 450;
        }
        if (isPet || isSiege || (isGuard && this.contract == null))
            this.currentID = (--Mob.staticID);
        else
            this.currentID = this.dbID;

        //store mobs by Database ID

        if (!isPet && !isSiege)
            Mob.mobMapByDBID.put(this.dbID, this);

    }

    /*
     * Getters
     */
    @Override
    public int getDBID() {
        return this.dbID;
    }

    public int getLoadID() {
        return loadID;
    }

    /*
     * Serialization
     */

    @Override
    public int getObjectUUID() {
        return currentID;
    }

    public float getSpawnX() {
        return this.statLat;
    }

    public float getSpawnY() {
        return this.statAlt;
    }

    public float getSpawnZ() {
        return this.statLon;
    }

    public float getSpawnRadius() {
        return this.spawnRadius;
    }

    public void setSpawnTime(int value) {
        this.spawnTime = value;
    }

    //use getSpawnTime instead. This is just for init tables
    public int getTrueSpawnTime() {
        return this.spawnTime;
    }

    public String getSpawnTimeAsString() {
        if (this.spawnTime == 0)
            return MBServerStatics.DEFAULT_SPAWN_TIME_MS / 1000 + " seconds (Default)";
        else
            return this.spawnTime + " seconds";

    }

    @Override
    public MobBase getMobBase() {
        return this.mobBase;
    }

    public int getMobBaseID() {

        if (this.mobBase != null)
            return this.mobBase.getObjectUUID();

        return 0;
    }

    public Vector3fImmutable getTrueBindLoc() {
        return this.bindLoc;
    }

    public Zone getParentZone() {
        return this.parentZone;
    }

    public int getParentZoneID() {

        if (this.parentZone != null)
            return this.parentZone.getObjectUUID();

        return 0;
    }

    @Override
    public int getGuildUUID() {

        if (this.guild == null)
            return 0;

        return this.guild.getObjectUUID();
    }

    @Override
    public PlayerCharacter getOwner() {

        if (!this.isPet())
            return null;

        if (this.ownerUID == 0)
            return null;

        return PlayerCharacter.getFromCache(this.ownerUID);
    }

    public void setOwner(PlayerCharacter value) {

        if (value == null)
            this.ownerUID = 0;
        else
            this.ownerUID = value.getObjectUUID();
    }

    public void setFearedObject(AbstractWorldObject awo) {
        this.fearedObject = awo;
    }

    @Override
    public Vector3fImmutable getBindLoc() {

        if (this.isPet() && !this.isSiege)
            return this.getOwner() != null ? this.getOwner().getLoc() : this.getLoc();
        return this.bindLoc;
    }

    public void calculateModifiedStats() {

        float strVal = this.mobBase.getMobBaseStats().getBaseStr();
        float dexVal = this.mobBase.getMobBaseStats().getBaseDex();
        float conVal = 0; // I believe this will desync the Mobs Health if we call it.
        float intVal = this.mobBase.getMobBaseStats().getBaseInt();
        float spiVal = this.mobBase.getMobBaseStats().getBaseSpi();

        // TODO modify for equipment
        if (this.bonuses != null) {
            // modify for effects
            strVal += this.bonuses.getFloat(ModType.Attr, SourceType.Strength);
            dexVal += this.bonuses.getFloat(ModType.Attr, SourceType.Dexterity);
            conVal += this.bonuses.getFloat(ModType.Attr, SourceType.Constitution);
            intVal += this.bonuses.getFloat(ModType.Attr, SourceType.Intelligence);
            spiVal += this.bonuses.getFloat(ModType.Attr, SourceType.Spirit);

            // apply dex penalty for armor
            // modify percent amounts. DO THIS LAST!
            strVal *= (1 + this.bonuses.getFloatPercentAll(ModType.Attr, SourceType.Strength));
            dexVal *= (1 + this.bonuses.getFloatPercentAll(ModType.Attr, SourceType.Dexterity));
            conVal *= (1 + this.bonuses.getFloatPercentAll(ModType.Attr, SourceType.Constitution));
            intVal *= (1 + this.bonuses.getFloatPercentAll(ModType.Attr, SourceType.Intelligence));
            spiVal *= (1 + this.bonuses.getFloatPercentAll(ModType.Attr, SourceType.Spirit));
        } else {
            // apply dex penalty for armor
        }

        // Set current stats
        this.statStrCurrent = (strVal < 1) ? (short) 1 : (short) strVal;
        this.statDexCurrent = (dexVal < 1) ? (short) 1 : (short) dexVal;
        this.statConCurrent = (conVal < 1) ? (short) 1 : (short) conVal;
        this.statIntCurrent = (intVal < 1) ? (short) 1 : (short) intVal;
        this.statSpiCurrent = (spiVal < 1) ? (short) 1 : (short) spiVal;

    }

    @Override
    public float getSpeed() {
        float bonus = 1;
        if (this.bonuses != null)
            // get rune and effect bonuses
            bonus *= (1 + this.bonuses.getFloatPercentAll(ModType.Speed, SourceType.None));

        if (this.isPlayerGuard)
            switch (this.mobBase.getLoadID()) {
                case 2111:
                    if (this.isWalk())
                        if (this.isCombat())
                            return Guards.HumanArcher.getWalkCombatSpeed() * bonus;
                        else
                            return Guards.HumanArcher.getWalkSpeed() * bonus;
                    else
                        return Guards.HumanArcher.getRunSpeed() * bonus;

                case 14103:
                    if (this.isWalk())
                        if (this.isCombat())
                            return Guards.UndeadArcher.getWalkCombatSpeed() * bonus;
                        else
                            return Guards.UndeadArcher.getWalkSpeed() * bonus;
                    else
                        return Guards.UndeadArcher.getRunSpeed() * bonus;
            }
        //return combat speeds
        //not combat return normal speeds
        if (this.isCombat())
            if (this.isWalk()) {
                if (this.mobBase.getWalkCombat() <= 0)
                    return MBServerStatics.MOB_SPEED_WALKCOMBAT * bonus;
                return this.mobBase.getWalkCombat() * bonus;
            } else {
                if (this.mobBase.getRunCombat() <= 0)
                    return MBServerStatics.MOB_SPEED_RUNCOMBAT * bonus;
                return this.mobBase.getRunCombat() * bonus;
            }
        else if (this.isWalk()) {
            if (this.mobBase.getWalk() <= 0)
                return MBServerStatics.MOB_SPEED_WALK * bonus;
            return this.mobBase.getWalk() * bonus;
        } else {
            if (this.mobBase.getRun() <= 0)
                return MBServerStatics.MOB_SPEED_RUN * bonus;
            return this.mobBase.getRun() * bonus;
        }

    }

    @Override
    public float getPassiveChance(String type, int AttackerLevel, boolean fromCombat) {
        //TODO add this later for dodge
        return 0f;
    }

    /*
     * Database
     */

    /**
     * @ Kill this Character
     */
    @Override
    public void killCharacter(AbstractCharacter attacker) {


        this.stopMovement(this.getMovementLoc());

        if (attacker != null)
            if (attacker.getObjectType() == GameObjectType.PlayerCharacter) {
                Group g = GroupManager.getGroup((PlayerCharacter) attacker);

                // Give XP, now handled inside the Experience Object
                if (!this.isPet() && !this.isNecroPet() && !(this.agentType.equals(AIAgentType.PET)) && !this.isPlayerGuard)
                    Experience.doExperience((PlayerCharacter) attacker, this, g);
            } else if (attacker.getObjectType().equals(GameObjectType.Mob)) {
                Mob mobAttacker = (Mob) attacker;

                if (mobAttacker.isPet()) {

                    PlayerCharacter owner = mobAttacker.getOwner();

                    if (owner != null)
                        if (!this.isPet() && !this.isNecroPet() && !(this.agentType.equals(AIAgentType.PET)) && !this.isPlayerGuard) {
                            Group g = GroupManager.getGroup(owner);

                            // Give XP, now handled inside the Experience Object
                            Experience.doExperience(owner, this, g);
                        }
                }
            }
        killCleanup();
    }

    public void updateLocation() {

        if (!this.isMoving())
            return;

        if (this.isAlive() == false || this.getBonuses().getBool(ModType.Stunned, SourceType.None) || this.getBonuses().getBool(ModType.CannotMove, SourceType.None)) {
            //Target is stunned or rooted. Don't move

            this.stopMovement(this.getMovementLoc());

            return;
        }

        Vector3fImmutable newLoc = this.getMovementLoc();

        if (newLoc.equals(this.getEndLoc())) {
            this.stopMovement(newLoc);
            this.region = AbstractWorldObject.GetRegionByWorldObject(this);
            return;
            //Next upda
        }

        setLoc(newLoc);
        this.region = AbstractWorldObject.GetRegionByWorldObject(this);
        //Next update will be end Loc, lets stop him here.

    }

    @Override
    public void killCharacter(String reason) {
        killCleanup();
    }

    private void killCleanup() {
        Dispatch dispatch;

        try {
            //resync corpses
            //this.setLoc(this.getMovementLoc());
            if (this.isSiege) {
                this.deathTime = System.currentTimeMillis();
                //this.state = STATE.Dead;
                try {
                    this.clearEffects();
                } catch (Exception e) {
                    Logger.error(e.getMessage());
                }
                this.setCombatTarget(null);
                this.hasLoot = false;
                this.playerAgroMap.clear();

                if (this.BehaviourType.ordinal() == Enum.MobBehaviourType.GuardMinion.ordinal())
                    this.spawnTime = (int) (-2.500 * ((Mob) this.npcOwner).building.getRank() + 22.5) * 60;

                if (this.isPet()) {

                    PlayerCharacter petOwner = this.getOwner();

                    if (petOwner != null) {
                        this.setOwner(null);
                        petOwner.setPet(null);
                        PetMsg petMsg = new PetMsg(5, null);
                        dispatch = Dispatch.borrow(this.getOwner(), petMsg);
                        DispatchMessage.dispatchMsgDispatch(dispatch, Enum.DispatchChannel.PRIMARY);
                    }
                }

            } else if (this.isPet() || this.isNecroPet()) {
                //this.state = STATE.Disabled;

                this.setCombatTarget(null);
                this.hasLoot = false;

                //if (this.parentZone != null)
                //this.parentZone.zoneMobSet.remove(this);
                ZoneManager.getSeaFloor().zoneMobSet.remove(this);
                try {
                    this.clearEffects();
                } catch (Exception e) {
                    Logger.error(e.getMessage());
                }
                this.playerAgroMap.clear();
                WorldGrid.RemoveWorldObject(this);

                DbManager.removeFromCache(this);
                PlayerCharacter petOwner = this.getOwner();

                if (petOwner != null) {
                    this.setOwner(null);
                    petOwner.setPet(null);
                    PetMsg petMsg = new PetMsg(5, null);
                    dispatch = Dispatch.borrow(petOwner, petMsg);
                    DispatchMessage.dispatchMsgDispatch(dispatch, Enum.DispatchChannel.PRIMARY);
                }
            } else {

                //cleanup effects
                playerAgroMap.clear();

                if (!this.isPlayerGuard && this.equip != null)
                    LootManager.GenerateEquipmentDrop(this);

            }
            try {
                this.clearEffects();
            } catch (Exception e) {
                Logger.error(e.getMessage());
            }

            this.combat = false;
            this.walkMode = true;
            this.setCombatTarget(null);

            this.hasLoot = this.charItemManager.getInventoryCount() > 0;
        } catch (Exception e) {
            Logger.error(e);
        }
        this.updateLocation();
    }

    public void respawn() {

        this.despawned = false;
        this.setCombatTarget(null);
        this.setHealth(this.healthMax);
        this.stamina.set(this.staminaMax);
        this.mana.set(this.manaMax);
        this.combat = false;
        this.walkMode = true;
        this.setCombatTarget(null);
        this.isAlive.set(true);
        this.deathTime = 0;
        this.lastBindLoc = this.bindLoc;
        this.setLoc(this.lastBindLoc);
        this.stopMovement(this.lastBindLoc);

        NPCManager.applyRuneSetEffects(this);

        this.recalculateStats();
        this.setHealth(this.healthMax);

        if (this.building == null && this.npcOwner != null && ((Mob) this.npcOwner).BehaviourType.ordinal() == MobBehaviourType.GuardCaptain.ordinal())
            this.building = ((Mob) this.npcOwner).building;
        else if (this.building != null)
            this.region = BuildingManager.GetRegion(this.building, bindLoc.x, bindLoc.y, bindLoc.z);

        //MovementManager.translocate(this, this.bindLoc, this.region);

        if (!this.isSiege && !this.isPlayerGuard && contract == null)
            loadInventory();

        this.updateLocation();
    }

    public void despawn() {

        this.despawned = true;

        WorldGrid.RemoveWorldObject(this);
        this.charItemManager.clearInventory();
    }

    @Override
    public boolean canBeLooted() {
        return !this.isAlive();
    }

    public int getTypeMasks() {

        if (this.mobBase == null)
            return 0;

        return this.mobBase.getTypeMasks();
    }

    /**
     * Clears and sets the inventory of the Mob. Must be called every time the
     * mob is spawned or respawned.
     */
    public void loadInventory() {

        if (!MBServerStatics.ENABLE_MOB_LOOT)
            return;

        this.charItemManager.clearInventory();
        this.charItemManager.clearEquip();

        if (isPlayerGuard)
            return;

        LootManager.GenerateMobLoot(this);
    }

    @Override
    public void updateDatabase() {
        //		DbManager.MobQueries.updateDatabase(this);
    }

    public void refresh() {
        if (this.isAlive())
            WorldGrid.updateObject(this);
    }

    public void recalculateStats() {

        try {
            calculateModifiedStats();
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }

        try {
            calculateAtrDefenseDamage();
        } catch (Exception e) {
            Logger.error(this.getMobBaseID() + " /" + e.getMessage());
        }
        try {
            calculateMaxHealthManaStamina();
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }

        Resists.calculateResists(this);
    }

    public void calculateMaxHealthManaStamina() {
        float h;
        float m;
        float s;

        h = this.mobBase.getHealthMax();
        if (this.isPet()) {
            h = this.level * 0.5f * 120;
        }
        m = this.statSpiCurrent;
        s = this.statConCurrent;

        // Apply any bonuses from runes and effects

        if (this.bonuses != null) {
            h += this.bonuses.getFloat(ModType.HealthFull, SourceType.None);
            m += this.bonuses.getFloat(ModType.ManaFull, SourceType.None);
            s += this.bonuses.getFloat(ModType.StaminaFull, SourceType.None);

            //apply effects percent modifiers. DO THIS LAST!

            h *= (1 + this.bonuses.getFloatPercentAll(ModType.HealthFull, SourceType.None));
            m *= (1 + this.bonuses.getFloatPercentAll(ModType.ManaFull, SourceType.None));
            s *= (1 + this.bonuses.getFloatPercentAll(ModType.StaminaFull, SourceType.None));
        }

        // Set max health, mana and stamina

        if (h > 0)
            this.healthMax = h;
        else
            this.healthMax = 1;

        if (m > -1)
            this.manaMax = m;
        else
            this.manaMax = 0;

        if (s > -1)
            this.staminaMax = s;
        else
            this.staminaMax = 0;

        // Update health, mana and stamina if needed

        if (this.getHealth() > this.healthMax)
            this.setHealth(this.healthMax);

        if (this.mana.get() > this.manaMax)
            this.mana.set(this.manaMax);

        if (this.stamina.get() > this.staminaMax)
            this.stamina.set(staminaMax);

    }

    public void calculateAtrDefenseDamage() {

        if (this.charItemManager == null || this.equip == null) {
            Logger.error("Player " + currentID + " missing skills or equipment");
            defaultAtrAndDamage(true);
            defaultAtrAndDamage(false);
            this.defenseRating = 0;
            return;
        }

        try {
            calculateAtrDamageForWeapon(this.equip.get(MBServerStatics.SLOT_MAINHAND), true);
        } catch (Exception e) {

            this.atrHandOne = (short) this.mobBase.getAttackRating();
            this.minDamageHandOne = (short) this.mobBase.getMinDmg();
            this.maxDamageHandOne = (short) this.mobBase.getMaxDmg();
            this.rangeHandOne = 6.5f;
            this.speedHandOne = 20;
            Logger.info("Mobbase ID " + this.getMobBaseID() + " returned an error. setting to default ATR and Damage." + e.getMessage());
        }

        try {
            calculateAtrDamageForWeapon(this.equip.get(MBServerStatics.SLOT_OFFHAND), false);

        } catch (Exception e) {

            this.atrHandTwo = (short) this.mobBase.getAttackRating();
            this.minDamageHandTwo = (short) this.mobBase.getMinDmg();
            this.maxDamageHandTwo = (short) this.mobBase.getMaxDmg();
            this.rangeHandTwo = 6.5f;
            this.speedHandTwo = 20;
            Logger.info("Mobbase ID " + this.getMobBaseID() + " returned an error. setting to default ATR and Damage." + e.getMessage());
        }

        try {
            float defense = this.mobBase.getDefenseRating();
            defense += getShieldDefense(equip.get(MBServerStatics.SLOT_OFFHAND));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_HELMET));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_CHEST));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_ARMS));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_GLOVES));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_LEGGINGS));
            defense += getArmorDefense(equip.get(MBServerStatics.SLOT_FEET));
            defense += getWeaponDefense(equip);

            // TODO add error log here
            if (this.bonuses != null) {

                // add any bonuses

                defense += (short) this.bonuses.getFloat(ModType.DCV, SourceType.None);

                // Finally, multiply any percent modifiers. DO THIS LAST!

                float pos_Bonus = 1 + this.bonuses.getFloatPercentPositive(ModType.DCV, SourceType.None);


                defense = (short) (defense * pos_Bonus);

                //Lucky rune applies next

                float neg_Bonus = this.bonuses.getFloatPercentNegative(ModType.DCV, SourceType.None);
                defense = (short) (defense * (1 + neg_Bonus));


            } else
                Logger.error("Error: missing bonuses");

            defense = (defense < 1) ? 1 : defense;
            this.defenseRating = (short) (defense + 0.5f);
        } catch (Exception e) {
            Logger.info("Mobbase ID " + this.getMobBaseID() + " returned an error. Setting to Default Defense." + e.getMessage());
            this.defenseRating = (short) this.mobBase.getDefense();
        }
        // calculate defense for equipment
    }

    private float getWeaponDefense(HashMap<Integer, MobEquipment> equipped) {

        MobEquipment weapon = equipped.get(MBServerStatics.SLOT_MAINHAND);
        ItemBase wb = null;
        CharacterSkill skill, mastery;
        float val = 0;
        boolean unarmed = false;

        if (weapon == null) {
            weapon = equipped.get(MBServerStatics.SLOT_OFFHAND);

            if (weapon == null)
                unarmed = true;
            else
                wb = weapon.getItemBase();

        } else
            wb = weapon.getItemBase();

        if (wb == null)
            unarmed = true;

        if (unarmed) {
            skill = null;
            mastery = null;
        } else {
            skill = this.skills.get(wb.getSkillRequired());
            mastery = this.skills.get(wb.getMastery());
        }

        if (skill != null)
            val += (int) skill.getModifiedAmount() / 2f;

        if (mastery != null)
            val += (int) mastery.getModifiedAmount() / 2f;

        return val;
    }

    private float getShieldDefense(MobEquipment shield) {

        if (shield == null)
            return 0;

        ItemBase ab = shield.getItemBase();

        if (ab == null || !ab.isShield())
            return 0;

        CharacterSkill blockSkill = this.skills.get("Block");
        float skillMod;

        if (blockSkill == null) {
            skillMod = CharacterSkill.getQuickMastery(this, "Block");

            if (skillMod == 0f)
                return 0;

        } else
            skillMod = blockSkill.getModifiedAmount();

        float def = ab.getDefense();

        //apply item defense bonuses

        return (def * (1 + ((int) skillMod / 100f)));
    }

    private float getArmorDefense(MobEquipment armor) {

        if (armor == null)
            return 0;

        ItemBase ib = armor.getItemBase();

        if (ib == null)
            return 0;

        if (!ib.getType().equals(ItemType.ARMOR))
            return 0;

        if (ib.getSkillRequired().isEmpty())
            return ib.getDefense();

        CharacterSkill armorSkill = this.skills.get(ib.getSkillRequired());

        if (armorSkill == null)
            return ib.getDefense();

        float def = ib.getDefense();

        //apply item defense bonuses

        return (def * (1 + ((int) armorSkill.getModifiedAmount() / 50f)));
    }

    private void calculateAtrDamageForWeapon(MobEquipment weapon, boolean mainHand) {

        int baseStrength = 0;

        float skillPercentage, masteryPercentage;
        float mastDam;

        // make sure weapon exists

        boolean noWeapon = false;
        ItemBase wb = null;

        if (weapon == null)
            noWeapon = true;

        else {

            ItemBase ib = weapon.getItemBase();

            if (ib == null)
                noWeapon = true;
            else if (ib.getType().equals(ItemType.WEAPON) == false) {
                defaultAtrAndDamage(mainHand);
                return;
            } else
                wb = ib;
        }

        float min, max;
        float speed;
        boolean strBased = false;

        // get skill percentages and min and max damage for weapons

        if (noWeapon) {

            if (mainHand)
                this.rangeHandOne = this.mobBase.getAttackRange();
            else
                this.rangeHandTwo = -1; // set to do not attack

            skillPercentage = getModifiedAmount(this.skills.get("Unarmed Combat"));
            masteryPercentage = getModifiedAmount(this.skills.get("Unarmed Combat Mastery"));

            if (masteryPercentage == 0f)
                mastDam = CharacterSkill.getQuickMastery(this, "Unarmed Combat Mastery");
            else
                mastDam = masteryPercentage;

            // TODO Correct these
            min = this.mobBase.getMinDmg();
            max = this.mobBase.getMaxDmg();
        } else {

            if (mainHand)
                this.rangeHandOne = weapon.getItemBase().getRange() * (1 + (baseStrength / 600.0f));
            else
                this.rangeHandTwo = weapon.getItemBase().getRange() * (1 + (baseStrength / 600.0f));

            skillPercentage = getModifiedAmount(this.skills.get(wb.getSkillRequired()));
            masteryPercentage = getModifiedAmount(this.skills.get(wb.getMastery()));

            if (masteryPercentage == 0f)
                mastDam = 0f;
            else
                mastDam = masteryPercentage;

            min = wb.getMinDamage();
            max = wb.getMaxDamage();
            strBased = wb.isStrBased();
        }

        // calculate atr
        float atr = this.mobBase.getAttackRating();

        if (this.statStrCurrent > this.statDexCurrent)
            atr += statStrCurrent * .5;
        else
            atr += statDexCurrent * .5;

        // add in any bonuses to atr

        if (this.bonuses != null) {
            atr += this.bonuses.getFloat(ModType.OCV, SourceType.None);

            // Finally use any multipliers. DO THIS LAST!
            float pos_Bonus = 1 + this.bonuses.getFloatPercentPositive(ModType.OCV, SourceType.None);

            atr *= pos_Bonus;

            //and negative percent modifiers
            //TODO DO DEBUFFS AFTER?? wILL TEst when finished
            float neg_Bonus = this.bonuses.getFloatPercentNegative(ModType.OCV, SourceType.None);

            atr *= (1 + neg_Bonus);
        }

        atr = (atr < 1) ? 1 : atr;

        // set atr

        if (mainHand)
            this.atrHandOne = (short) (atr + 0.5f);
        else
            this.atrHandTwo = (short) (atr + 0.5f);

        //calculate speed

        if (wb != null)
            speed = wb.getSpeed();
        else
            speed = 20f; //unarmed attack speed

        if (this.bonuses != null && this.bonuses.getFloat(ModType.AttackDelay, SourceType.None) != 0f) //add effects speed bonus
            speed *= (1 + this.bonuses.getFloatPercentAll(ModType.AttackDelay, SourceType.None));

        if (speed < 10)
            speed = 10;

        //add min/max damage bonuses for weapon  **REMOVED

        //if duel wielding, cut damage by 30%
        // calculate damage

        float minDamage;
        float maxDamage;
        float pri = (strBased) ? (float) this.statStrCurrent : (float) this.statDexCurrent;
        float sec = (strBased) ? (float) this.statDexCurrent : (float) this.statStrCurrent;

        minDamage = (float) (min * ((0.0315f * Math.pow(pri, 0.75f)) + (0.042f * Math.pow(sec, 0.75f)) + (0.01f * ((int) skillPercentage + (int) mastDam))));
        maxDamage = (float) (max * ((0.0785f * Math.pow(pri, 0.75f)) + (0.016f * Math.pow(sec, 0.75f)) + (0.0075f * ((int) skillPercentage + (int) mastDam))));

        minDamage = (float) ((int) (minDamage + 0.5f)); //round to nearest decimal
        maxDamage = (float) ((int) (maxDamage + 0.5f)); //round to nearest decimal

        //add Base damage last.
        float minDamageMod = this.mobBase.getDamageMin();
        float maxDamageMod = this.mobBase.getDamageMax();

        minDamage += minDamageMod;
        maxDamage += maxDamageMod;

        // add in any bonuses to damage

        if (this.bonuses != null) {
            // Add any base bonuses
            minDamage += this.bonuses.getFloat(ModType.MinDamage, SourceType.None);
            maxDamage += this.bonuses.getFloat(ModType.MaxDamage, SourceType.None);

            // Finally use any multipliers. DO THIS LAST!
            minDamage *= (1 + this.bonuses.getFloatPercentAll(ModType.MinDamage, SourceType.None));
            maxDamage *= (1 + this.bonuses.getFloatPercentAll(ModType.MaxDamage, SourceType.None));
        }

        // set damages

        if (mainHand) {
            this.minDamageHandOne = (short) minDamage;
            this.maxDamageHandOne = (short) maxDamage;
            this.speedHandOne = 30;
        } else {
            this.minDamageHandTwo = (short) minDamage;
            this.maxDamageHandTwo = (short) maxDamage;
            this.speedHandTwo = 30;
        }
    }

    private void defaultAtrAndDamage(boolean mainHand) {

        if (mainHand) {
            this.atrHandOne = 0;
            this.minDamageHandOne = 0;
            this.maxDamageHandOne = 0;
            this.rangeHandOne = -1;
            this.speedHandOne = 20;
        } else {
            this.atrHandTwo = 0;
            this.minDamageHandTwo = 0;
            this.maxDamageHandTwo = 0;
            this.rangeHandTwo = -1;
            this.speedHandTwo = 20;
        }
    }


    public ItemBase getWeaponItemBase(boolean mainHand) {

        if (this.equipmentSetID != 0)
            if (equip != null) {
                MobEquipment me;

                if (mainHand)
                    me = equip.get(1); //mainHand
                else
                    me = equip.get(2); //offHand

                if (me != null) {

                    ItemBase ib = me.getItemBase();

                    if (ib != null)
                        return ib;

                }
            }
        MobBase mb = this.mobBase;

        if (mb != null)
            if (equip != null) {

                MobEquipment me;

                if (mainHand)
                    me = equip.get(1); //mainHand
                else
                    me = equip.get(2); //offHand

                if (me != null)
                    return me.getItemBase();
            }
        return null;
    }

    @Override
    public void runAfterLoad() {

        // Initialize inventory

        this.charItemManager = new CharacterItemManager(this);
        this.loadInventory();
        try {
            if (this.equipmentSetID != 0)
                this.equip = MobBase.loadEquipmentSet(this.equipmentSetID);
            else
                this.equip = new HashMap<>();

        } catch (Exception e) {
            Logger.error(e.getMessage());
        }

        // Powers from mobbase

        if (PowersManager.AllMobPowers.containsKey(this.getMobBaseID()))
            for (MobPowerEntry mobPowerEntry : PowersManager.AllMobPowers.get(this.getMobBaseID()))
                mobPowers.put(mobPowerEntry.token, mobPowerEntry.rank);

        // Powers from contract

        if (this.contract != null && PowersManager.AllMobPowers.containsKey(this.contract.getContractID()))
            for (MobPowerEntry mobPowerEntry : PowersManager.AllMobPowers.get(this.contract.getContractID()))
                mobPowers.put(mobPowerEntry.token, mobPowerEntry.rank);

        if (this.equip == null) {
            Logger.error("Null equipset returned for uuid " + currentID);
            this.equip = new HashMap<>(0);
        }

        // Combine mobbase and mob aggro arrays into one bitvector
        //skip for pets

        if (this.isPet() == false && (this.agentType.equals(AIAgentType.PET)) == false && this.isNecroPet() == false) {
            if (this.getMobBase().notEnemy.size() > 0)
                this.notEnemy.addAll(this.getMobBase().notEnemy);

            if (this.getMobBase().enemy.size() > 0)
                this.enemy.addAll(this.getMobBase().enemy);
        }

        try {
            NPCManager.applyRuneSetEffects(this);
            recalculateStats();
            this.setHealth(this.healthMax);

            // Set bounds for this mobile

            Bounds mobBounds = Bounds.borrow();
            mobBounds.setBounds(this.getLoc());
            this.setBounds(mobBounds);

            if (this.contract != null && this.contract.getContractID() == 910) {
                this.isPlayerGuard = true;
                this.BehaviourType = MobBehaviourType.GuardCaptain;
                this.spawnTime = 900;
                this.guardedCity = ZoneManager.getCityAtLocation(this.bindLoc);
            }
            //assign 5 random patrol points for regular mobs

            if (!(this.agentType.equals(AIAgentType.GUARD)) && !this.isPlayerGuard() && !this.isPet() && !this.isNecroPet() && !(this.agentType.equals(AIAgentType.PET)) && !(this.agentType.equals(AIAgentType.CHARMED))) {
                this.patrolPoints = new ArrayList<>();

                for (int i = 0; i < 5; ++i) {
                    float patrolRadius = this.getSpawnRadius();

                    if (patrolRadius > 256)
                        patrolRadius = 256;

                    if (patrolRadius < 60)
                        patrolRadius = 60;

                    Vector3fImmutable newPatrolPoint = Vector3fImmutable.getRandomPointInCircle(this.getBindLoc(), patrolRadius);
                    this.patrolPoints.add(newPatrolPoint);

                    if (i == 1)
                        MovementManager.translocate(this, newPatrolPoint, null);
                }
            }

            if (this.BehaviourType == null)
                this.BehaviourType = this.getMobBase().fsm;

            if (this.isPlayerGuard() && this.contract != null)
                if (NPC.ISWallArcher(this.getContract())) {
                    this.BehaviourType = MobBehaviourType.GuardWallArcher;
                    this.spawnTime = 450;
                } else {
                    this.BehaviourType = MobBehaviourType.GuardCaptain;
                    this.spawnTime = 900;
                    this.guardedCity = ZoneManager.getCityAtLocation(this.bindLoc);
                }

            this.deathTime = 0;
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    @Override
    protected ConcurrentHashMap<Integer, CharacterPower> initializePowers() {
        return new ConcurrentHashMap<>(MBServerStatics.CHM_INIT_CAP, MBServerStatics.CHM_LOAD, MBServerStatics.CHM_THREAD_LOW);
    }

    public boolean canSee(PlayerCharacter target) {
        return this.mobBase.getSeeInvis() >= target.getHidden();
    }

    public int getBuildingID() {
        return buildingID;
    }

    public void setBuildingID(int buildingID) {
        this.buildingID = buildingID;
    }

    public boolean isSiege() {
        return isSiege;
    }

    public void setSiege(boolean isSiege) {
        this.isSiege = isSiege;
    }

    public void setNpcOwner(AbstractCharacter npcOwner) {
        this.npcOwner = npcOwner;
    }

    public boolean isNecroPet() {
        return this.mobBase.isNecroPet();
    }

    public void handleDirectAggro(AbstractCharacter ac) {

        if (!ac.getObjectType().equals(GameObjectType.PlayerCharacter))
            return;

        PlayerCharacter player = (PlayerCharacter) ac;

        if (this.getCombatTarget() == null) {
            this.setCombatTarget(ac);
            return;
        }

        if (player.getObjectUUID() == this.getCombatTarget().getObjectUUID())
            return;

        if (this.getCombatTarget().getObjectType() == GameObjectType.PlayerCharacter)
            if (ac.getHateValue() > ((PlayerCharacter) this.getCombatTarget()).getHateValue())
                this.setCombatTarget(player);
    }

    public void setRank(int newRank) {

        DbManager.MobQueries.SET_PROPERTY(this, "mob_level", newRank);
        this.level = (short) newRank;

    }

    public boolean isRanking() {

        return this.upgradeDateTime != null;
    }

    public long getLastAttackTime() {
        return lastAttackTime;
    }

    public void setLastAttackTime(long lastAttackTime) {
        this.lastAttackTime = lastAttackTime;
    }

    public void setDeathTime(long deathTime) {
        this.deathTime = deathTime;
    }

    public boolean isHasLoot() {
        return hasLoot;
    }

    public DeferredPowerJob getWeaponPower() {
        return weaponPower;
    }

    public void setWeaponPower(DeferredPowerJob weaponPower) {
        this.weaponPower = weaponPower;
    }

    public ConcurrentHashMap<Mob, Integer> getSiegeMinionMap() {
        return siegeMinionMap;
    }

    public DateTime getUpgradeDateTime() {

        lock.readLock().lock();

        try {
            return upgradeDateTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public boolean isPlayerGuard() {
        return isPlayerGuard;
    }

    public void setPlayerGuard(boolean isPlayerGuard) {
        this.isPlayerGuard = isPlayerGuard;
    }

    public void setPatrolPointIndex(int patrolPointIndex) {
    }

    public int getLastMobPowerToken() {
        return lastMobPowerToken;
    }

    public void setLastMobPowerToken(int lastMobPowerToken) {
        this.lastMobPowerToken = lastMobPowerToken;
    }


    public boolean isLootSync() {
        return lootSync;
    }

    public void setLootSync(boolean lootSync) {
        this.lootSync = lootSync;
    }

    public HashMap<Integer, MobEquipment> getEquip() {
        return equip;
    }

    public String getNameOverride() {
        return firstName + "  " + lastName;
    }

    public void processUpgradeMob(PlayerCharacter player) {

        lock.writeLock().lock();

        try {

            // Cannot upgrade an npc not within a building

            if (building == null)
                return;

            // Cannot upgrade an npc at max rank

            if (this.getRank() == 7)
                return;

            // Cannot upgrade an npc who is currently ranking

            if (this.isRanking())
                return;

            int rankCost = Mob.getUpgradeCost(this);

            // SEND NOT ENOUGH GOLD ERROR

            if (rankCost > building.getStrongboxValue()) {
                sendErrorPopup(player, 127);
                return;
            }

            try {

                if (!building.transferGold(-rankCost, false))
                    return;

                DateTime dateToUpgrade = DateTime.now().plusHours(Mob.getUpgradeTime(this));
                Mob.setUpgradeDateTime(this, dateToUpgrade);

                // Schedule upgrade job

                Mob.submitUpgradeJob(this);

            } catch (Exception e) {
                PlaceAssetMsg.sendPlaceAssetError(player.getClientConnection(), 1, "A Serious error has occurred. Please post details for to ensure transaction integrity");
            }

        } catch (Exception e) {
            Logger.error(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void dismiss() {

        if (this.isPet()) {

            if ((this.agentType.equals(AIAgentType.PET))) { //delete summoned pet
                this.despawn();
                WorldGrid.RemoveWorldObject(this);
                DbManager.removeFromCache(this);

                if (this.getObjectType() == GameObjectType.Mob)
                    if (this.getParentZone() != null)
                        this.getParentZone().zoneMobSet.remove(this);

            } else { //revert charmed pet
                this.agentType = AIAgentType.MOBILE;
                this.setCombatTarget(null);
            }
            //clear owner

            PlayerCharacter owner = this.getOwner();

            //close pet window

            if (owner != null) {
                Mob pet = owner.getPet();
                PetMsg pm = new PetMsg(5, null);
                Dispatch dispatch = Dispatch.borrow(owner, pm);
                DispatchMessage.dispatchMsgDispatch(dispatch, Enum.DispatchChannel.SECONDARY);

                if (pet != null && pet.getObjectUUID() == this.getObjectUUID())
                    owner.setPet(null);

                if (this.getObjectType().equals(GameObjectType.Mob))
                    this.setOwner(null);
            }
        }
    }

}