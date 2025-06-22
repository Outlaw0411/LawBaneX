package engine.OutpostSystem;

import engine.Enum;
import engine.gameManager.DbManager;
import engine.objects.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class OutpostManager {

    public static ArrayList<Outpost> outposts;
    //comments
    // 1. top of every hour, ownership reverts to NPC city
    // 2. allow outposts to be claimed at any time, but reverting control to NPC at top of hour

    public static void init(){
        outposts = new ArrayList<>();
        ConcurrentHashMap<Integer, AbstractGameObject> worldCities = DbManager.getMap(Enum.GameObjectType.City);

        //add npc cities
        for (AbstractGameObject ago : worldCities.values()) {
            City city = (City)ago;
            if(city.getIsNpcOwned() == 1 && city.getCityName().contains("Outpost")){
                int rank = city.getRank();
                Building tol = city.getTOL();
                Guild owner = city.getGuild();
                Mine mine = Mine.getMineFromTower(tol.getObjectUUID());
                outposts.add(new Outpost(owner,tol,rank, mine));
            }
        }
    }

    public static void revertAll(){
        for(Outpost outpost : outposts) {
            outpost.tower.setOwner(NPC.getNPC(outpost.originalOwner.getGuildLeaderUUID()));
            outpost.tower.setRank(outpost.rank);
            outpost.mine.setActive(true);
        }
    }
}
