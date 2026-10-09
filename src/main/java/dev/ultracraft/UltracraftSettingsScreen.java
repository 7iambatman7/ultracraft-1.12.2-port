package dev.ultracraft;

import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** Settings GUI for features that are implemented by the Forge 1.12.2 port. */
public final class UltracraftSettingsScreen extends GuiScreen {
    private static final int PAGE_GAMEPLAY = 0;
    private static final int PAGE_PERFORMANCE = 1;
    private static final int PAGE_MUSIC = 2;
    private static final int PAGE_CHEATS = 3;
    private int page;
    private int left;
    private int colWidth;

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.left = Math.max(8, (this.width - 420) / 2);
        this.colWidth = 202;
        int tabY = 32;
        addButton(100, left, tabY, 100, 20, "Gameplay");
        addButton(101, left + 104, tabY, 100, 20, "Performance");
        addButton(102, left + 208, tabY, 100, 20, "Music");
        addButton(103, left + 312, tabY, 100, 20, "Cheats");
        buildPage();
        this.buttonList.add(new GuiButton(900, this.width / 2 - 100, this.height - 28, 200, 20, I18n.format("gui.done")));
    }

    private void buildPage() {
        int y = 66;
        int h = 20;
        int gap = 4;
        if (page == PAGE_GAMEPLAY) {
            addToggle(1, 0, y, "Auto V1", UltracraftConfig.autoV1); y += h + gap;
            addToggle(2, 0, y, "Launch ULTRAKILL with Minecraft", UltracraftConfig.launchUltrakill); y += h + gap;
            addToggle(3, 0, y, "Sharp shop screen", UltracraftConfig.sharpShop); y += h + gap;
            addToggle(4, 0, y, "Player block damage", UltracraftConfig.playerBlockDamage); y += h + gap;
            addToggle(5, 0, y, "Enemy block damage", UltracraftConfig.enemyBlockDamage); y += h + gap;
            addCycle(6, 0, y, "Impact frames", String.format(Locale.ROOT, "%.1f", UltracraftConfig.impactFrames)); y += h + gap;
            addCycle(7, 1, 66, "Terrain export range", Integer.toString(UltracraftConfig.terrainRange));
        } else if (page == PAGE_PERFORMANCE) {
            addCycle(10, 0, y, "ULTRAKILL render height", Integer.toString(UltracraftConfig.v1Height)); y += h + gap;
            addCycle(11, 0, y, "ULTRAKILL FPS cap", UltracraftConfig.ukFps == 0 ? "Match Minecraft" : Integer.toString(UltracraftConfig.ukFps)); y += h + gap;
            addToggle(12, 0, y, "Low-latency frames", UltracraftConfig.lowLatency); y += h + gap;
            addToggle(13, 0, y, "Lock-step frame pacing", UltracraftConfig.lockStep); y += h + gap;
            addCycle(14, 0, y, "Effects quality", effectsName(UltracraftConfig.effects)); y += h + gap;
            addCycle(15, 0, y, "Stain limit", Integer.toString(UltracraftConfig.stainCap)); y += h + gap;
            addToggle(16, 1, 66, "Extra gore effects", UltracraftConfig.extraGore);
        } else if (page == PAGE_MUSIC) {
            addCycle(20, 0, y, "Fight music", UltracraftConfig.fightMusic); y += h + gap;
            addToggle(21, 0, y, "Calm music", UltracraftConfig.calmMusic);
        } else {
            addToggle(30, 0, y, "Never hungry (Minecraft side)", UltracraftConfig.cheatNeverHungry); y += h + gap;
            addToggle(31, 0, y, "Super speed (Steve only)", UltracraftConfig.cheatSuperSpeed);
        }
    }

    private void addToggle(int id, int column, int y, String label, boolean value) {
        addButton(id, left + column * (colWidth + 8), y, colWidth, 20, label + ": " + (value ? "ON" : "OFF"));
    }

    private void addCycle(int id, int column, int y, String label, String value) {
        addButton(id, left + column * (colWidth + 8), y, colWidth, 20, label + ": " + value);
    }

    private void addButton(int id, int x, int y, int w, int h, String label) {
        this.buttonList.add(new GuiButton(id, x, y, w, h, label));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 100 && button.id <= 103) {
            page = button.id - 100;
            initGui();
            return;
        }
        if (button.id == 900) {
            saveAndClose();
            return;
        }
        switch (button.id) {
            case 1: UltracraftConfig.autoV1 = !UltracraftConfig.autoV1; break;
            case 2: UltracraftConfig.launchUltrakill = !UltracraftConfig.launchUltrakill; break;
            case 3: UltracraftConfig.sharpShop = !UltracraftConfig.sharpShop; break;
            case 4: UltracraftConfig.playerBlockDamage = !UltracraftConfig.playerBlockDamage; break;
            case 5: UltracraftConfig.enemyBlockDamage = !UltracraftConfig.enemyBlockDamage; break;
            case 6: UltracraftConfig.impactFrames = nextFloat(UltracraftConfig.impactFrames, new float[] {0.5f, 1.0f, 1.5f, 2.0f, 3.0f}); break;
            case 7: UltracraftConfig.terrainRange = UltracraftConfig.terrainRange >= 128 ? 64 : UltracraftConfig.terrainRange + 16; break;
            case 10: UltracraftConfig.v1Height = nextValue(UltracraftConfig.v1Height, new int[] {0, 480, 540, 720, 1080, 1440}); break;
            case 11: UltracraftConfig.ukFps = nextValue(UltracraftConfig.ukFps, new int[] {0, 30, 60, 90, 120, 144, 240}); break;
            case 12: UltracraftConfig.lowLatency = !UltracraftConfig.lowLatency; break;
            case 13: UltracraftConfig.lockStep = !UltracraftConfig.lockStep; break;
            case 14: UltracraftConfig.effects = (UltracraftConfig.effects + 1) % 3; break;
            case 15: UltracraftConfig.stainCap = nextValue(UltracraftConfig.stainCap, new int[] {0, 512, 2048, 4096, 8192}); break;
            case 16: UltracraftConfig.extraGore = !UltracraftConfig.extraGore; break;
            case 20: UltracraftConfig.fightMusic = nextString(UltracraftConfig.fightMusic, new String[] {"off", "random", "levels", "cybergrind"}); break;
            case 21: UltracraftConfig.calmMusic = !UltracraftConfig.calmMusic; break;
            case 30: UltracraftConfig.cheatNeverHungry = !UltracraftConfig.cheatNeverHungry; break;
            case 31: UltracraftConfig.cheatSuperSpeed = !UltracraftConfig.cheatSuperSpeed; break;
            default: return;
        }
        UltracraftConfig.save();
        UltracraftConfig.sendOpts();
        initGui();
    }

    private static int nextValue(int current, int[] values) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private static float nextFloat(float current, float[] values) {
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - current) < 0.01f) return values[(i + 1) % values.length];
        return values[0];
    }

    private static String nextString(String current, String[] values) {
        for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        return values[0];
    }

    private static String effectsName(int value) {
        return value == 2 ? "High" : value == 1 ? "Medium" : "Low";
    }

    private void saveAndClose() {
        UltracraftConfig.save();
        UltracraftConfig.sendOpts();
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        UltracraftConfig.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRenderer, "Ultracraft 1.0.4 Settings", this.width / 2, 12, 0xFFFFFF);
        String pageName = page == PAGE_GAMEPLAY ? "Gameplay" : page == PAGE_PERFORMANCE ? "Performance" : page == PAGE_MUSIC ? "Music" : "Cheats";
        drawCenteredString(this.fontRenderer, pageName, this.width / 2, 56, 0xAAAAAA);
        if (page == PAGE_CHEATS) {
            drawCenteredString(this.fontRenderer, "Only the listed Forge-side cheats are implemented.", this.width / 2, this.height - 48, 0xBBBBBB);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
