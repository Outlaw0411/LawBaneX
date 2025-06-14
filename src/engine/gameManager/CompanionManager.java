package engine.gameManager;

import engine.Enum;
import engine.InterestManagement.InterestManager;
import engine.InterestManagement.WorldGrid;
import engine.math.Vector3fImmutable;
import engine.mobileAI.utilities.CombatUtilities;
import engine.mobileAI.utilities.MovementUtilities;
import engine.net.DispatchMessage;
import engine.net.client.msg.ErrorPopupMsg;
import engine.net.client.msg.UpdateStateMsg;
import engine.net.client.msg.VendorDialogMsg;
import engine.objects.*;
import engine.server.MBServerStatics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import static engine.Enum.CompanionType.*;

public class CompanionManager {

    // Use synchronizedList for thread safety if accessed from multiple threads
    public static final ArrayList<Mob> allCompanions = new ArrayList<>();

    public static void hireCompanion(PlayerCharacter pc, int id, Enum.CompanionType type) {
        if (pc == null) return;

        if (pc.companions == null)
            pc.companions = new ArrayList<>();

        if (pc.companions.size() >= 3) {
            ErrorPopupMsg.sendErrorMsg(pc, "You already have 3 companions.");
            return;
        }

        Guild guild = pc.guild;
        Mob companion = Mob.createCompanion(id, guild, ZoneManager.getSeaFloor(), pc, pc.level);
        if (companion != null) {
            companion.companionType = type;
            companion.setOwner(pc);
            pc.companions.add(companion);
            companion.setLoc(pc.loc);
            WorldGrid.addObject(companion, pc.loc.x, pc.loc.z);
            allCompanions.add(companion);
            switch (id) {
                case 2006:
                    companion.equipmentSetID = 6993;
                    companion.setFirstName("Healer Companion");
                    break;
                case 2010:
                    companion.equipmentSetID = 8614;
                    companion.setFirstName("Tank Companion");
                    break;
                case 2017:
                    companion.equipmentSetID = 7328;
                    companion.setFirstName("Melee Companion");
                    break;
                case 2009:
                    companion.equipmentSetID = 8616;
                    companion.setFirstName("Caster Companion");
                    break;
                case 2002:
                    companion.equipmentSetID = 7203;
                    companion.setFirstName("Ranged Companion");
                    break;
            }
            try {
                companion.runAfterLoad();
            } catch (Exception e) {
                e.printStackTrace();
            }
            InterestManager.reloadCharacter(companion);
            WorldGrid.updateObject(companion);

            HashSet<AbstractWorldObject> players = WorldGrid.getObjectsInRangePartial(companion.loc,
                    MBServerStatics.CHARACTER_LOAD_RANGE, MBServerStatics.MASK_PLAYER);
            for (AbstractWorldObject awo : players) {
                if (awo instanceof PlayerCharacter)
                    ((PlayerCharacter) awo).setDirtyLoad(true);
            }
        }
    }

    public static VendorDialog processDialog(VendorDialogMsg msg, PlayerCharacter pc) {
        VendorDialog vd = VendorDialog.getHostileVendorDialog();

        // Change the greeting message as requested
        //vd.setMessage("Welcome, traveler! Seeking a trusted ally for your next adventure? My companions never run from a fight.");

        vd.getOptions().clear();
        switch (msg.getUnknown03()) {
            default:
                // Show hire options if no specific selection made
                vd.getOptions().add(new MenuOption(999, "Hire Healer", 999));
                vd.getOptions().add(new MenuOption(998, "Hire Tank", 998));
                vd.getOptions().add(new MenuOption(997, "Hire Melee", 997));
                vd.getOptions().add(new MenuOption(996, "Hire Caster", 996));
                vd.getOptions().add(new MenuOption(995, "Hire Ranged", 995));
                break;
            case 999: // healer
                hireCompanion(pc, 2006, HEALER);
                break;
            case 998: // tank
                hireCompanion(pc, 2010, TANK);
                break;
            case 997: // melee
                hireCompanion(pc, 2017, MELEE);
                break;
            case 996: // caster
                hireCompanion(pc, 2009, CASTER);
                break;
            case 995: // ranged
                hireCompanion(pc, 2002, RANGED);
                break;
        }
        return vd;
    }

    public static void pulseCompanions() {
        // Remove dead or ownerless companions
        ArrayList<Mob> toRemove = new ArrayList<>();
        for (Mob companion : allCompanions) {
            if (companion == null || !companion.isAlive() || companion.getOwner() == null) {
                if (companion != null && companion.getOwner() != null && companion.getOwner().companions != null) {
                    companion.getOwner().companions.remove(companion);
                }
                if (companion != null)
                    WorldGrid.removeObject(companion);
                toRemove.add(companion);
            }
        }
        allCompanions.removeAll(toRemove);

        // AI Pulse logic
        for (Mob companion : allCompanions) {
            try {
                if (companion.getOwner() == null || companion.loc == null || companion.getOwner().loc == null)
                    continue;

                companion.updateLocation();
                companion.setBindLoc(companion.getOwner().loc.x, companion.getOwner().loc.y, companion.getOwner().loc.z);

                // Update combat state - only for non-healers
                if (companion.companionType != HEALER) {
                    if (companion.combatTarget != null && !companion.combatTarget.isAlive())
                        companion.setCombatTarget(null);

                    if (companion.combatTarget == null && companion.getOwner().combatTarget != null)
                        companion.combatTarget = companion.getOwner().combatTarget;

                    if (companion.getOwner().combatTarget != null && companion.combatTarget != null &&
                            !companion.combatTarget.equals(companion.getOwner().combatTarget))
                        companion.combatTarget = companion.getOwner().combatTarget;

                    if (companion.combatTarget != null) {
                        if (!companion.isCombat()) {
                            companion.setCombat(true);
                            UpdateStateMsg rwss = new UpdateStateMsg();
                            rwss.setPlayer(companion);
                            DispatchMessage.dispatchMsgToInterestArea(companion, rwss,
                                    Enum.DispatchChannel.PRIMARY, MBServerStatics.CHARACTER_LOAD_RANGE, true, false);
                        }
                    } else {
                        if (companion.isCombat()) {
                            companion.setCombat(false);
                            UpdateStateMsg rwss = new UpdateStateMsg();
                            rwss.setPlayer(companion);
                            DispatchMessage.dispatchMsgToInterestArea(companion, rwss,
                                    Enum.DispatchChannel.PRIMARY, MBServerStatics.CHARACTER_LOAD_RANGE, true, false);
                        }
                    }
                }

                // Teleport if too far from owner
                if (companion.loc.distance2D(companion.getOwner().loc) > MBServerStatics.CHARACTER_LOAD_RANGE) {
                    companion.teleport(Vector3fImmutable.getRandomPointOnCircle(companion.getOwner().loc, 6f));
                    companion.setCombatTarget(null);
                }

                // Movement logic
                if (companion.combatTarget == null) {
                    float distanceToOwner = companion.loc.distance2D(companion.getOwner().loc);
                    if (distanceToOwner > 10f) {
                        if (distanceToOwner > MBServerStatics.CHARACTER_LOAD_RANGE) {
                            companion.teleport(Vector3fImmutable.getRandomPointOnCircle(companion.getOwner().loc, 6f));
                            companion.setCombatTarget(null);
                            HashSet<AbstractWorldObject> players = WorldGrid.getObjectsInRangePartial(companion.loc,
                                    MBServerStatics.CHARACTER_LOAD_RANGE, MBServerStatics.MASK_PLAYER);
                            for (AbstractWorldObject awo : players) {
                                if (awo instanceof PlayerCharacter)
                                    ((PlayerCharacter) awo).setDirtyLoad(true);
                            }
                        } else {
                            MovementUtilities.aiMove(companion,
                                    Vector3fImmutable.getRandomPointOnCircle(companion.getOwner().loc, 6f), false);
                        }
                    }
                } else {
                    if (companion.companionType == CASTER) {
                        if (!CombatUtilities.inRange2D(companion, companion.combatTarget, 30f)) {
                            Vector3fImmutable location = companion.combatTarget.loc.moveTowards(companion.loc, 29f);
                            MovementUtilities.aiMove(companion, location, false);
                        }
                    } else {
                        Vector3fImmutable location =
                                companion.combatTarget.loc.moveTowards(companion.loc, companion.getRange() - 1);
                        MovementUtilities.aiMove(companion, location, false);
                    }
                }

                // Per-type AI pulse
                switch (companion.companionType) {
                    case HEALER:
                        pulseHealer(companion, companion.getOwner());
                        break;
                    case TANK:
                        pulseTank(companion, companion.getOwner());
                        break;
                    case MELEE:
                    case RANGED:
                        pulseAttack(companion, companion.getOwner());
                        break;
                    case CASTER:
                        pulseCaster(companion, companion.getOwner());
                        break;
                }
            } catch (Exception e) {
                // Log and continue
                e.printStackTrace();
            }
        }
    }

    public static void pulseHealer(Mob mob, PlayerCharacter owner) {
        if (mob == null || owner == null) return;
        if (mob.isMoving()) return;
        if (owner.getHealth() < owner.healthMax) {
            if (System.currentTimeMillis() > mob.nextCastTime) {
                PowersManager.applyPower(mob, owner, owner.loc, 429506720, 40, false);
                mob.nextCastTime = System.currentTimeMillis() + 10000L;
            }
        }
    }

    public static void pulseTank(Mob mob, PlayerCharacter owner) {
        if (mob == null) return;
        if (System.currentTimeMillis() > mob.nextCastTime) {
            HashSet<AbstractWorldObject> mobs = WorldGrid.getObjectsInRangePartial(mob.loc, 64f, MBServerStatics.MASK_MOB);
            for (AbstractWorldObject awo : mobs) {
                if (awo instanceof Mob)
                    ((Mob) awo).setCombatTarget(mob);
            }
            mob.nextCastTime = System.currentTimeMillis() + 10000L;
        }
        pulseAttack(mob, owner);
    }

    public static void pulseAttack(Mob mob, PlayerCharacter owner) {
        if (mob == null || mob.combatTarget == null) return;
        if (System.currentTimeMillis() > mob.getLastAttackTime()) {
            float range = mob.getRange();
            float distance = mob.loc.distance2D(mob.combatTarget.loc);
            if (range >= distance) {
                int attackDelay = 3000;
                CombatUtilities.combatCycle(mob, mob.combatTarget, true, mob.getWeaponItemBase(true));
                CombatUtilities.combatCycle(mob, mob.combatTarget, false, mob.getWeaponItemBase(false));
                mob.setLastAttackTime(System.currentTimeMillis() + attackDelay);
            }
        } else {
            MovementUtilities.aiMove(mob, mob.combatTarget.loc.moveTowards(mob.loc, mob.getRange() - 2), false);
        }
    }

    public static void pulseCaster(Mob mob, PlayerCharacter owner) {
        if (mob == null || owner == null || owner.combatTarget == null) return;
        if (CombatUtilities.inRange2D(mob, owner.combatTarget, 30)
                && System.currentTimeMillis() > mob.nextCastTime) {
            PowersManager.applyPower(mob, owner.combatTarget, owner.combatTarget.loc, 429757701, 40, false);
            mob.nextCastTime = System.currentTimeMillis() + 10000L;
        }
    }
}