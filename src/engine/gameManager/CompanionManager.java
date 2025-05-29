package engine.gameManager;

import engine.Enum;
import engine.InterestManagement.InterestManager;
import engine.InterestManagement.WorldGrid;
import engine.math.Vector3fImmutable;
import engine.mobileAI.MobAI;
import engine.mobileAI.utilities.CombatUtilities;
import engine.mobileAI.utilities.MovementUtilities;
import engine.net.client.msg.VendorDialogMsg;
import engine.objects.*;
import engine.server.MBServerStatics;

import java.util.ArrayList;
import java.util.HashSet;

import static engine.Enum.CompanionType.*;

public class CompanionManager {

    public static ArrayList<Mob> allCompanions = new ArrayList<>();

    public static void HireCompanion(PlayerCharacter pc, int id, Enum.CompanionType type){
        Guild guild = pc.guild;
        Mob companion = Mob.createCompanion(id,guild,ZoneManager.getSeaFloor(),pc,pc.level);
        if(companion != null){
            companion.companionType = type;
            companion.setOwner(pc);
            pc.companions.add(companion);
            companion.setLoc(pc.loc);
            WorldGrid.addObject(companion,pc.loc.x,pc.loc.z);
            allCompanions.add(companion);
            switch(id){
                case 2006:
                    companion.equipmentSetID = 6993;
                    break;
                case 2010:
                    companion.equipmentSetID = 8614;
                    break;
                case 2017:
                    companion.equipmentSetID = 7328;
                    break;
                case 2009:
                    companion.equipmentSetID = 8616;
                    break;
                case 2002:
                    companion.equipmentSetID = 7203;
                    break;
            }
            companion.runAfterLoad();
            InterestManager.forceLoad(companion);
            WorldGrid.updateObject(companion);
            InterestManager.reloadCharacter(companion);
        }
    }

    public static VendorDialog processDialog(VendorDialogMsg msg, PlayerCharacter pc){
        VendorDialog vd;
        vd = VendorDialog.getHostileVendorDialog();
        vd.getOptions().clear();
        switch(msg.getUnknown03()) {
            default:
            MenuOption healer = new MenuOption(999, "Hire Healer", 999);
            vd.getOptions().add(healer);
            MenuOption tank = new MenuOption(998, "Hire Tank", 998);
            vd.getOptions().add(tank);
            MenuOption melee = new MenuOption(997, "Hire Melee", 997);
            vd.getOptions().add(melee);
            MenuOption caster = new MenuOption(996, "Hire Caster", 996);
            vd.getOptions().add(caster);
            MenuOption ranged = new MenuOption(995, "Hire Ranged", 995);
            vd.getOptions().add(ranged);
            break;
            case 999: // healer
                HireCompanion(pc,2006, HEALER);
                break;
            case 998: // tank
                HireCompanion(pc,2010, TANK);
                break;
            case 997: //melee
                HireCompanion(pc,2017, Enum.CompanionType.MELEE);
                break;
            case 996: // caster
                HireCompanion(pc,2009, CASTER);
                break;
            case 995: // ranged
                HireCompanion(pc,2002, Enum.CompanionType.RANGED);
                break;
        }
        return vd;
    }

    public static void pulse_companions(){
        ArrayList<Mob> toRemove = new ArrayList<>();
        for(Mob companion : allCompanions) {
            if (!companion.isAlive()) {
                toRemove.add(companion);
                companion.getOwner().companions.remove(companion);
            }
        }

        allCompanions.removeAll(toRemove);

        for(Mob companion : allCompanions){

            if(!companion.companionType.equals(TANK) && !companion.companionType.equals(HEALER))
                companion.combatTarget = companion.getOwner().combatTarget;

            if(companion.combatTarget == null) {
                if (companion.loc.distanceSquared2D(companion.getOwner().loc) > (32f * 32f)) {
                    if (companion.loc.distanceSquared2D(companion.getOwner().loc) > (500f * 500f)) {
                        companion.teleport(Vector3fImmutable.getRandomPointOnCircle(companion.getOwner().loc, 12f));
                    } else {
                        if (!companion.isMoving())
                            MovementUtilities.aiMove(companion, Vector3fImmutable.getRandomPointOnCircle(companion.getOwner().loc, 12f), false);
                    }
                }
            }else{
                if (companion.companionType.equals(CASTER)) {
                    if(!CombatUtilities.inRange2D(companion,companion.combatTarget, 30f)){
                        Vector3fImmutable location = companion.combatTarget.loc.moveTowards(companion.loc,29f);
                        MovementUtilities.aiMove(companion, location, false);
                    }
                }else if (companion.companionType.equals(RANGED)) {
                    Vector3fImmutable location = companion.combatTarget.loc.moveTowards(companion.loc,companion.getRange() - 1);
                    MovementUtilities.aiMove(companion, location, false);
                }else{

                }
            }
            switch(companion.companionType){
                case HEALER:
                    pulseHealer(companion,companion.getOwner());
                    break;
                case TANK:
                    pulseTank(companion,companion.getOwner());
                    break;
                case MELEE:
                    pulseMelee(companion,companion.getOwner());
                    break;
                case CASTER:
                    pulseCaster(companion,companion.getOwner());
                    break;
                case RANGED:
                    pulseRanged(companion,companion.getOwner());
                    break;
            }
        }
    }

    public static void pulseHealer(Mob mob, PlayerCharacter owner){
            if(mob.isMoving())
                return;
            if(owner.getHealth() < owner.healthMax) {
                if (System.currentTimeMillis() < mob.nextCastTime) {
                    PowersManager.useMobPower(mob, owner, PowersManager.getPowerByToken(429506720), 40);
                    mob.nextCastTime = System.currentTimeMillis() + 10000L;
                }
            }
    }
    public static void pulseTank(Mob mob, PlayerCharacter owner){

        if(System.currentTimeMillis() < mob.nextCastTime){
            HashSet<AbstractWorldObject> mobs = WorldGrid.getObjectsInRangePartial(mob.loc,32f, MBServerStatics.MASK_MOB);
            for(AbstractWorldObject awo : mobs){
                ((Mob)awo).setCombatTarget(mob);
            }
            mob.nextCastTime = System.currentTimeMillis() + 10000L;
        }
    }
    public static void pulseMelee(Mob mob, PlayerCharacter owner){

        if (mob.isMoving())
            return;
            if(CombatUtilities.inRangeToAttack(mob,owner.combatTarget)) {
                MobAI.CheckForAttack(mob);
            }
    }
    public static void pulseCaster(Mob mob, PlayerCharacter owner){
            if(CombatUtilities.inRange2D(mob,owner.combatTarget,30) && System.currentTimeMillis() < mob.nextCastTime){
                PowersManager.useMobPower(mob,owner,PowersManager.getPowerByToken(429757701),40);
                mob.nextCastTime = System.currentTimeMillis() + 10000L;
            }
        }
    public static void pulseRanged(Mob mob, PlayerCharacter owner){
            if(CombatUtilities.inRangeToAttack(mob,owner.combatTarget)) {
                MobAI.CheckForAttack(mob);
            }
    }
}
