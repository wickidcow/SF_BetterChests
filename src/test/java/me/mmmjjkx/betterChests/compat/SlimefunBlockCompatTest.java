package me.mmmjjkx.betterChests.compat;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Uses explicit API doubles, not a simulated world or database durability claim. */
class SlimefunBlockCompatTest {
    private final Location location = new Location(null, -30000, -64, 30000);
    private SlimefunBlockCompat.Access access;
    @BeforeEach void reset() {
        Api.manager = new Manager();
        access = SlimefunBlockCompat.createAccess(Api.class);
    }
    @Test void existingLoadedValueRemainsExact() {
        Api.manager.controller.data.values.put("stored", "9007199254740993");
        assertEquals("9007199254740993", access.getData(location, "stored"));
        assertEquals(0, Api.manager.controller.loads);
    }
    @Test void identityReadUsesRealIdentityRatherThanMetadataShadow() {
        Api.manager.controller.data.values.put("id", "WRONG_SHADOW");
        assertEquals("BC_STORAGE", access.getData(location, "id"));
    }
    @Test void unloadedDataLoadsExactlyOnce() {
        Api.manager.controller.data.loaded = false;
        access.getData(location, "stored"); access.getBlockMenu(location);
        assertEquals(1, Api.manager.controller.loads);
    }
    @Test void failedLoadIsNotAnEmptyRecord() {
        Api.manager.controller.data.loaded = false;
        Api.manager.controller.finishLoad = false;
        assertThrows(IllegalStateException.class, () -> access.getData(location, "stored"));
    }
    @Test void failedLoadCannotAcceptWrites() {
        Data d = Api.manager.controller.data; d.loaded = false;
        Api.manager.controller.finishLoad = false;
        assertThrows(IllegalStateException.class, () -> access.setData(location, "stored", "0"));
        assertEquals(0, d.writes);
    }
    @Test void absentReadRemainsAbsent() {
        Api.manager.controller.data = null;
        assertNull(access.getData(location, "stored"));
        assertNull(access.getBlockMenu(location));
    }
    @Test void absentWriteIsNotSilentlySuccessful() {
        Api.manager.controller.data = null;
        assertThrows(IllegalStateException.class, () -> access.setData(location, "stored", "1"));
    }
    @Test void valuesAreNeverCached() {
        access.setData(location, "stored", "3");
        assertEquals("3", access.getData(location, "stored"));
        Api.manager.controller.data.values.put("stored", "5");
        assertEquals("5", access.getData(location, "stored"));
    }
    @Test void replacedBlockRecordIsNotCached() {
        access.setData(location, "stored", "7");
        Data old = Api.manager.controller.data;
        Api.manager.controller.data = new Data();
        access.setData(location, "stored", "9");
        assertEquals("7", old.values.get("stored"));
        assertEquals("9", Api.manager.controller.data.values.get("stored"));
    }
    @Test void databaseManagerIsResolvedOnEachOperation() {
        access.setData(location, "stored", "7");
        Api.manager = new Manager();
        assertNull(access.getData(location, "stored"));
    }
    @Test void nullWriteRemovesOnlyRequestedKey() {
        access.setData(location, "stored", "7");
        access.setData(location, "owner", "preserved");
        access.setData(location, "stored", null);
        assertNull(access.getData(location, "stored"));
        assertEquals("preserved", access.getData(location, "owner"));
        assertEquals(1, Api.manager.controller.data.removals);
    }
    @Test void emptyStringIsNotConvertedToRemoval() {
        access.setData(location, "bc_drawer_item_v2", "");
        assertEquals("", access.getData(location, "bc_drawer_item_v2"));
        assertEquals(0, Api.manager.controller.data.removals);
    }
    @Test void identityCannotBeRewrittenAsMetadata() {
        assertThrows(IllegalArgumentException.class, () -> access.setData(location, "id", "REPLACEMENT"));
        assertEquals(0, Api.manager.controller.data.writes);
        assertEquals("BC_STORAGE", access.getData(location, "id"));
    }
    @Test void originalRuntimeCauseIsPreserved() {
        RuntimeException expected = new IllegalArgumentException("refused write");
        Api.manager.controller.data.failure = expected;
        assertSame(expected, assertThrows(IllegalArgumentException.class,
                () -> access.setData(location, "stored", "0")));
    }
    @Test void linkageFailureDoesNotInvokeLegacyFallback() {
        Api.manager.controller.failLinkage = true;
        assertThrows(NoSuchMethodError.class, () -> access.getData(location, "stored"));
    }
    @Test void incompleteModernApiIsNotMisclassifiedAsOldApi() {
        assertThrows(IllegalStateException.class, () -> SlimefunBlockCompat.createAccess(BrokenApi.class));
    }
    @Test void trulyOldApiRetainsIsolatedFallback() {
        assertEquals("LegacyAccess", SlimefunBlockCompat.createAccess(OldApi.class).getClass().getSimpleName());
    }
    @Test void menuReadsDoNotWriteAnyMetadata() {
        assertNull(access.getBlockMenu(location));
        assertEquals(0, Api.manager.controller.data.writes);
        assertEquals(0, Api.manager.controller.data.removals);
    }
    @Test void clickFlagsPreserveAllFourCombinations() {
        for (boolean right : new boolean[]{false,true}) for (boolean shift : new boolean[]{false,true}) {
            var action = new LegacyMenuCompat.ClickActionView(right,shift);
            assertEquals(right,action.isRightClicked()); assertEquals(shift,action.isShiftClicked());
        }
    }
    @Test void malformedLegacyStreamsRemainRejected() {
        assertNull(LegacyItemStackSerialization.deserialize(new byte[0]));
        assertNull(LegacyItemStackSerialization.deserialize(new byte[]{1,2,3,4}));
    }

    public static class OldApi {}
    public static class BrokenApi { public static Object getDatabaseManager() { return new Object(); } }
    public static class Api {
        static Manager manager;
        public static Manager getDatabaseManager() { return manager; }
    }
    public static class Manager {
        final Controller controller = new Controller();
        public Controller getBlockDataController() { return controller; }
    }
    public static class Controller {
        Data data = new Data(); int loads; boolean finishLoad = true, failLinkage;
        public Data getBlockData(Location location) {
            if (failLinkage) throw new NoSuchMethodError("changed provider");
            return data;
        }
        public void loadBlockData(Data value) { loads++; value.loaded = finishLoad; }
    }
    public static class Data {
        boolean loaded = true; int writes, removals; RuntimeException failure;
        final Map<String,String> values = new HashMap<>();
        public boolean isDataLoaded() { return loaded; }
        public String getSfId() { return "BC_STORAGE"; }
        public String getData(String key) { return values.get(key); }
        public void setData(String key,String value) {
            if (failure != null) throw failure;
            writes++; values.put(key,value);
        }
        public void removeData(String key) { removals++; values.remove(key); }
        public BlockMenu getBlockMenu() { return null; }
    }
}
