package gjum.minecraft.civ.snitchmod.common.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.civ.snitchmod.common.SnitchMod;
import gjum.minecraft.civ.snitchmod.common.model.Snitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class SnitchesGui extends Screen {
    private static final int ENTRY_HEIGHT = 20;
    private static final int MARGIN = 10;
    private static final int BUTTON_HEIGHT = 20;
    
    private final List<Snitch> allSnitches;
    private List<Snitch> filteredSnitches;
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    
    private Button sortByGroupButton;
    private Button sortByStatusButton;
    private Button sortByRefreshedButton;
    private Button sortByDistanceButton;
    
    private EditBox searchBox;
    private EditBox renameBox;
    private Snitch selectedSnitch = null;
    
    private SortMode currentSort = SortMode.DISTANCE;
    private boolean sortAscending = true;
    
    private enum SortMode {
        GROUP, STATUS, REFRESHED, DISTANCE, NAME
    }
    
    public SnitchesGui() {
        super(Component.literal("Snitches Manager"));
        
        // Get all snitches from the store
        var store = SnitchMod.getMod().getStore();
        if (store != null) {
            this.allSnitches = new ArrayList<>(store.getAllSnitches());
            System.out.println("[SnitchesGui] Loaded " + this.allSnitches.size() + " snitches from store");
        } else {
            this.allSnitches = new ArrayList<>();
            System.out.println("[SnitchesGui] Store is null, no snitches loaded");
        }
        
        this.filteredSnitches = new ArrayList<>(this.allSnitches);
        updateSort();
        System.out.println("[SnitchesGui] After filtering: " + this.filteredSnitches.size() + " snitches");
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Search box
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 30, 200, 20, Component.literal("Search..."));
        this.addRenderableWidget(this.searchBox);
        
        // Sort buttons
        int buttonY = 55;
        this.sortByGroupButton = Button.builder(Component.literal("Group"), button -> sortBy(SortMode.GROUP))
                .bounds(MARGIN, buttonY, 80, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.sortByGroupButton);
        
        this.sortByStatusButton = Button.builder(Component.literal("Status"), button -> sortBy(SortMode.STATUS))
                .bounds(MARGIN + 85, buttonY, 80, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.sortByStatusButton);
        
        this.sortByRefreshedButton = Button.builder(Component.literal("Refreshed"), button -> sortBy(SortMode.REFRESHED))
                .bounds(MARGIN + 170, buttonY, 80, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.sortByRefreshedButton);
        
        this.sortByDistanceButton = Button.builder(Component.literal("Distance"), button -> sortBy(SortMode.DISTANCE))
                .bounds(MARGIN + 255, buttonY, 80, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.sortByDistanceButton);
        
        // Rename section
        this.renameBox = new EditBox(this.font, this.width - 200, this.height - 60, 150, 20, Component.literal("Internal name..."));
        this.addRenderableWidget(this.renameBox);
        
        Button renameButton = Button.builder(Component.literal("Set Name"), button -> setInternalName())
                .bounds(this.width - 45, this.height - 60, 40, 20).build();
        this.addRenderableWidget(renameButton);
        
        // Close button
        Button closeButton = Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build();
        this.addRenderableWidget(closeButton);
    }
    
    private void sortBy(SortMode mode) {
        if (currentSort == mode) {
            sortAscending = !sortAscending;
        } else {
            currentSort = mode;
            sortAscending = true;
        }
        updateSort();
    }
    
    private void updateSort() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            System.out.println("[SnitchesGui] Player is null, cannot sort by distance");
            return;
        }
        
        var playerPos = mc.player.position();
        
        Comparator<Snitch> comparator = switch (currentSort) {
            case GROUP -> Comparator.comparing(s -> s.getGroup() != null ? s.getGroup() : "");
            case STATUS -> Comparator.comparing(this::getSnitchStatus);
            case REFRESHED -> Comparator.comparing(Snitch::getLastSeenTs);
            case DISTANCE -> Comparator.comparing(s -> s.pos.getCenter().distanceTo(playerPos));
            case NAME -> Comparator.comparing(s -> s.getName() != null ? s.getName() : "");
        };
        
        if (!sortAscending) {
            comparator = comparator.reversed();
        }
        
        filteredSnitches = allSnitches.stream()
                .filter(this::matchesSearch)
                .sorted(comparator)
                .collect(Collectors.toList());
                
        maxScrollOffset = Math.max(0, filteredSnitches.size() - getVisibleEntries());
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);
    }
    
    private boolean matchesSearch(Snitch snitch) {
        if (searchBox == null || searchBox.getValue().trim().isEmpty()) {
            return true;
        }
        
        String search = searchBox.getValue().toLowerCase();
        String name = snitch.getName() != null ? snitch.getName().toLowerCase() : "";
        String group = snitch.getGroup() != null ? snitch.getGroup().toLowerCase() : "";
        String coords = snitch.pos.getX() + " " + snitch.pos.getY() + " " + snitch.pos.getZ();
        
        return name.contains(search) || group.contains(search) || coords.contains(search);
    }
    
    private String getSnitchStatus(Snitch snitch) {
        if (snitch.wasBroken()) return "broken";
        if (snitch.isGone()) return "gone";
        if (snitch.hasCullTs() && System.currentTimeMillis() > snitch.getCullTs()) return "culled";
        if (snitch.hasDormantTs() && System.currentTimeMillis() > snitch.getDormantTs()) return "dormant";
        return "alive";
    }
    
    private int getVisibleEntries() {
        return (this.height - 120) / ENTRY_HEIGHT;
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        
        // Sort indicator
        String sortText = currentSort.name() + (sortAscending ? " ▲" : " ▼");
        guiGraphics.drawString(this.font, sortText, this.width - 100, 40, 0xFFFFFFFF, false);
        
        // Debug info - show count
        String countText = "Showing " + filteredSnitches.size() + " of " + allSnitches.size() + " snitches";
        guiGraphics.drawString(this.font, countText, MARGIN, 40, 0xFFFFFFFF, false);
        
        // Snitches list
        int listY = 80;
        int visibleEntries = getVisibleEntries();
        
        for (int i = 0; i < visibleEntries && (i + scrollOffset) < filteredSnitches.size(); i++) {
            Snitch snitch = filteredSnitches.get(i + scrollOffset);
            int y = listY + i * ENTRY_HEIGHT;
            
            boolean isSelected = snitch == selectedSnitch;
            if (isSelected) {
                guiGraphics.fill(MARGIN, y, this.width - MARGIN, y + ENTRY_HEIGHT - 2, 0x80FFFFFF);
            }
            
            // Snitch info with proper white color
            String snitchInfo = formatSnitchInfo(snitch);
            int textColor = isSelected ? 0x000000 : 0xFFFFFFFF; // Black text on selection, white otherwise
            guiGraphics.drawString(this.font, snitchInfo, MARGIN + 5, y + 5, textColor, false);
        }
        
        // Selected snitch details
        if (selectedSnitch != null) {
            String details = String.format("Selected: %s at %d %d %d", 
                    selectedSnitch.getName() != null ? selectedSnitch.getName() : "Unnamed",
                    selectedSnitch.pos.getX(), selectedSnitch.pos.getY(), selectedSnitch.pos.getZ());
            guiGraphics.drawString(this.font, details, MARGIN, this.height - 80, 0xFFFFFFFF, false);
        }
    }
    
    private String formatSnitchInfo(Snitch snitch) {
        var playerPos = Minecraft.getInstance().player.position();
        double distance = snitch.pos.getCenter().distanceTo(playerPos);
        
        String name = snitch.getName() != null ? snitch.getName() : "Unnamed";
        String group = snitch.getGroup() != null ? snitch.getGroup() : "Unknown";
        String status = getSnitchStatus(snitch);
        String coords = String.format("%d,%d,%d", snitch.pos.getX(), snitch.pos.getY(), snitch.pos.getZ());
        
        return String.format("%-15s %-10s %-8s %s (%.1fm)", name, group, status, coords, distance);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked in snitches list
        int listY = 80;
        int visibleEntries = getVisibleEntries();
        
        if (mouseX >= MARGIN && mouseX <= this.width - MARGIN && mouseY >= listY) {
            int clickedIndex = (int) (mouseY - listY) / ENTRY_HEIGHT;
            if (clickedIndex < visibleEntries && (clickedIndex + scrollOffset) < filteredSnitches.size()) {
                selectedSnitch = filteredSnitches.get(clickedIndex + scrollOffset);
                if (selectedSnitch != null && renameBox != null) {
                    // Pre-fill rename box with current name or internal name
                    renameBox.setValue(selectedSnitch.getName() != null ? selectedSnitch.getName() : "");
                }
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - (int)scrollY));
        return true;
    }
    
    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            // Re-filter when search changes
            updateSort();
        }
    }
    
    private void setInternalName() {
        if (selectedSnitch != null && renameBox != null && !renameBox.getValue().trim().isEmpty()) {
            String newName = renameBox.getValue().trim();
            
            // Store internal name (we'll need to add this functionality to Snitch)
            selectedSnitch.setInternalName(newName);
            
            // Update the store
            var store = SnitchMod.getMod().getStore();
            if (store != null) {
                store.updateSnitch(selectedSnitch);
            }
            
            // Log to chat
            Minecraft.getInstance().gui.getChat().addMessage(
                    Component.literal("[SnitchMod] Set internal name: " + newName)
            );
            
            updateSort(); // Refresh the list
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }
}