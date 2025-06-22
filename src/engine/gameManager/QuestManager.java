package engine.gameManager;

import engine.math.Bounds;
import engine.net.client.msg.ErrorPopupMsg;
import engine.objects.*;

import java.util.HashMap;

public class QuestManager {
    public enum QuestType{
        EXPLORE,
        TALK
    }

    public static HashMap<Integer, QuestBase> quests_by_id = new HashMap<>();

    public static void check_completion(QuestBase qb, PlayerCharacter pc){
        switch(qb.type){
            case EXPLORE:
                completeEXPLORE(qb,pc);
                break;
            case TALK:
                completeTALK(qb,pc);
                break;
        }
    }

    public static void completeEXPLORE(QuestBase qb, PlayerCharacter pc){
        Zone zone  = ZoneManager.getZoneByZoneID(qb.refID);
        if(Bounds.collide(pc.loc,zone.getBounds())) {
            completeQuest(qb, pc);
        }
    }

    public static void completeTALK(QuestBase qb, PlayerCharacter pc){
        NPC npc  = NPC.getNPC(qb.refID);
        if(pc.getLastNPCDialog().equals(npc)) {
            completeQuest(qb, pc);
        }
    }


    public static void completeQuest(QuestBase qb, PlayerCharacter pc){
        pc.grantXP(qb.xp);
        pc.getCharItemManager().addGoldToInventory(qb.gold,false);
        ItemBase ib = ItemBase.getItemBase(qb.rewardID);
        if(ib != null){
            pc.getCharItemManager().addItemToInventory(new MobLoot(null,ib,false).promoteToItem(pc));
            pc.getCharItemManager().updateInventory();
        }
        ErrorPopupMsg.sendErrorMsg(pc, "You Have Completed The Quest");
        pc.current_quest = null;
    }
}
