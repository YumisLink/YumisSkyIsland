package cn.yumis.skyisland.server;

import cn.yumis.skyisland.data.IslandState;
import java.math.BigInteger;
import java.util.UUID;

/** Credits and level receipts must be called inside the same durable state edit. */
public final class PersonalTokens {
    private PersonalTokens() {}
    public static void credit(IslandState.Person person,int amount) {
        if(amount<0)throw new IllegalArgumentException("代币奖励不能为负");
        person.tokens=person.tokens.add(BigInteger.valueOf(amount));
    }
    public static void quest(IslandState state,IslandState.Party party,UUID owner,boolean shared,int amount) {
        if(shared)for(UUID id:party.members){var person=state.players.get(id);if(person!=null)credit(person,amount);}
        else credit(state.players.get(owner),amount);
    }
    public static void levels(IslandState state,IslandState.Party party,int before,int after,int amount) {
        if(after<=before)return;
        for(UUID id:party.members) {
            var person=state.players.get(id);if(person==null)continue;
            for(long level=(long)before+1;level<=after;level++)if(person.rewardedTokenLevels.add((int)level))credit(person,amount);
        }
    }
}
