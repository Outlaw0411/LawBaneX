package engine.OutpostSystem;

import engine.objects.Building;
import engine.objects.Guild;
import engine.objects.Mine;

public class Outpost {
    public final Guild originalOwner;
    public final Building tower;
    public final int rank;
    public final Mine mine;

    public Outpost(Guild origin, Building tower, int rank, Mine mine){
        this.originalOwner = origin;
        this.tower = tower;
        this.rank = rank;
        this.mine = mine;
    }
}
