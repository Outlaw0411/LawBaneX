package engine.objects;

import engine.gameManager.QuestManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class QuestBase {
    public final int uid;
    public final QuestManager.QuestType type;
    public final int level;
    public final int xp;
    public final int gold;
    public final int rewardID;
    public final int refID;

    public QuestBase(ResultSet rs) throws SQLException {
        this.uid = rs.getInt("UID");
        this.type = QuestManager.QuestType.valueOf(rs.getString("quest_type"));
        this.level = rs.getInt("quest_level");
        this.xp = rs.getInt("quest_xp");
        this.gold = rs.getInt("quest_gold");
        this.rewardID = rs.getInt("quest_reward_itemBaseID");
        this.refID = rs.getInt("quest_ref_uid");
    }
}
