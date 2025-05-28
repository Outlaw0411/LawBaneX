package engine.gameManager;

import engine.Enum;
import engine.InterestManagement.InterestManager;
import engine.InterestManagement.WorldGrid;
import engine.math.Vector3fImmutable;
import engine.mobileAI.MobAI;
import engine.mobileAI.utilities.CombatUtilities;
import engine.mobileAI.utilities.MovementUtilities;
import engine.objects.AbstractWorldObject;
import engine.objects.Guild;
import engine.objects.Mob;
import engine.objects.PlayerCharacter;
import engine.server.MBServerStatics;

import java.util.ArrayList;
import java.util.HashSet;

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
            InterestManager.forceLoad(companion);
            allCompanions.add(companion);
        }
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
        if(mob.loc.distanceSquared2D(owner.loc) > (32f*32f)){
            if(mob.loc.distanceSquared2D(owner.loc) > 500f*500f){
                mob.teleport(owner.loc);
            }else {
                MovementUtilities.aiMove(mob, owner.loc, false);
            }
        }else{
            if(mob.isMoving())
                mob.stopMovement(mob.getMovementLoc());
            if(owner.getHealth() < owner.healthMax){
                if(System.currentTimeMillis() < mob.nextCastTime){
                    PowersManager.useMobPower(mob,owner,PowersManager.getPowerByToken(429506720),40);
                    mob.nextCastTime = System.currentTimeMillis() + 10000L;
                }
            }
        }
    }
    public static void pulseTank(Mob mob, PlayerCharacter owner){
        if(mob.loc.distanceSquared2D(owner.loc) > (32f*32f)){
            if(mob.loc.distanceSquared2D(owner.loc) > 500f*500f){
                mob.teleport(owner.loc);
            }else {
                MovementUtilities.aiMove(mob, owner.loc, false);
            }
        }else {
            if (mob.isMoving())
                mob.stopMovement(mob.getMovementLoc());
        }
        if(System.currentTimeMillis() < mob.nextCastTime){
            HashSet<AbstractWorldObject> mobs = WorldGrid.getObjectsInRangePartial(mob.loc,32f, MBServerStatics.MASK_MOB);
            for(AbstractWorldObject awo : mobs){
                ((Mob)awo).setCombatTarget(mob);
            }
            mob.nextCastTime = System.currentTimeMillis() + 10000L;
        }
    }
    public static void pulseMelee(Mob mob, PlayerCharacter owner){
        if(mob.loc.distanceSquared2D(owner.loc) > (32f*32f)){
            if(mob.loc.distanceSquared2D(owner.loc) > 500f*500f){
                mob.teleport(owner.loc);
            }else {
                MovementUtilities.aiMove(mob, owner.loc, false);
            }
        }else {
            if (mob.isMoving())
                mob.stopMovement(mob.getMovementLoc());
        }

        if(owner.combatTarget != null){
            mob.setCombatTarget(owner.combatTarget);
            if(CombatUtilities.inRangeToAttack(mob,owner.combatTarget)) {
                MobAI.CheckForAttack(mob);
            }else{
                Vector3fImmutable location = owner.combatTarget.loc.moveTowards(mob.loc,mob.getRange() - 1);
                MovementUtilities.aiMove(mob, location, false);
            }
        }
    }
    public static void pulseCaster(Mob mob, PlayerCharacter owner){
        if(mob.loc.distanceSquared2D(owner.loc) > (32f*32f)){
            if(mob.loc.distanceSquared2D(owner.loc) > 500f*500f){
                mob.teleport(owner.loc);
            }else {
                MovementUtilities.aiMove(mob, owner.loc, false);
            }
        }else {
            if (mob.isMoving())
                mob.stopMovement(mob.getMovementLoc());
        }

        if(owner.combatTarget != null){
            mob.setCombatTarget(owner.combatTarget);
            if(CombatUtilities.inRange2D(mob,owner.combatTarget,30)) {
                MobAI.CheckForAttack(mob);
            }else{
                Vector3fImmutable location = owner.combatTarget.loc.moveTowards(mob.loc,29);
                MovementUtilities.aiMove(mob, location, false);
            }
            if(System.currentTimeMillis() < mob.nextCastTime){
                PowersManager.useMobPower(mob,owner,PowersManager.getPowerByToken(429757701),40);
                mob.nextCastTime = System.currentTimeMillis() + 10000L;
            }
        }
    }
    public static void pulseRanged(Mob mob, PlayerCharacter owner){
        if(mob.loc.distanceSquared2D(owner.loc) > (32f*32f)){
            if(mob.loc.distanceSquared2D(owner.loc) > 500f*500f){
                mob.teleport(owner.loc);
            }else {
                MovementUtilities.aiMove(mob, owner.loc, false);
            }
        }else {
            if (mob.isMoving())
                mob.stopMovement(mob.getMovementLoc());
        }

        if(owner.combatTarget != null){
            mob.setCombatTarget(owner.combatTarget);
            if(CombatUtilities.inRangeToAttack(mob,owner.combatTarget)) {
                MobAI.CheckForAttack(mob);
            }else{
                Vector3fImmutable location = owner.combatTarget.loc.moveTowards(mob.loc,mob.getRange() - 1);
                MovementUtilities.aiMove(mob, location, false);
            }
        }
    }
}
