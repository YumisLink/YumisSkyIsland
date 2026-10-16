package cn.yumis.skyisland.core;

/** Original block evidence captured on the server before a harvest changes it. */
public record HarvestContext(String sourceBlockId, int age) {}
