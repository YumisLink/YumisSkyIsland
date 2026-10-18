package cn.yumis.skyisland.sorting;

import java.util.*;

/** Stored in the world state journal; block entities carry only their stable device ID. */
public final class SorterData {
    public UUID id,party;
    public String dimension;
    public long position,epoch,revision;
    public boolean paused;
    public Map<Long,String> source=new LinkedHashMap<>(),target=new LinkedHashMap<>();
}
