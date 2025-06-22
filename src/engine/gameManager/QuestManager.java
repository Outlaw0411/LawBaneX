package engine.gameManager;

import engine.objects.ItemBase;
import engine.objects.MobLoot;
import engine.objects.PlayerCharacter;
import engine.objects.QuestBase;

import java.util.HashMap;

public class QuestManager {
    public enum QuestType{
        TEST
    }

    public static HashMap<Integer, QuestBase> quests_by_id = new HashMap<>();

    public static void completeQuest(QuestBase qb, PlayerCharacter pc){
        pc.grantXP(qb.xp);
        pc.getCharItemManager().addGoldToInventory(qb.gold,false);
        ItemBase ib = ItemBase.getItemBase(qb.rewardID);
        if(ib != null){
            pc.getCharItemManager().addItemToInventory(new MobLoot(null,ib,false).promoteToItem(pc));
            pc.getCharItemManager().updateInventory();
        }
    }
}
