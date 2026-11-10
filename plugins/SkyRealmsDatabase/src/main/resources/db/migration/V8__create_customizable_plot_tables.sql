-- ==============================================================================
-- SkyRealms Garden Customizable Plot System Migration (V8)
-- Plot customization metadata, crop beds, and saved player layouts.
-- Preserves all existing plot unlocks, collections, mastery, and journal data.
-- ==============================================================================

-- 1. Extend sr_garden_plots with layout, irrigation, decoration, and upgrade columns
ALTER TABLE sr_garden_plots ADD COLUMN IF NOT EXISTS selected_layout VARCHAR(64) NOT NULL DEFAULT 'CLEAN_CANVAS';
ALTER TABLE sr_garden_plots ADD COLUMN IF NOT EXISTS irrigation_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE sr_garden_plots ADD COLUMN IF NOT EXISTS decoration_style VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE sr_garden_plots ADD COLUMN IF NOT EXISTS upgrade_level INTEGER NOT NULL DEFAULT 1;
ALTER TABLE sr_garden_plots ADD COLUMN IF NOT EXISTS expanded_radius INTEGER NOT NULL DEFAULT 12;

-- 2. Crop Bed Definitions (plot-local coordinates inside the 62x62 plot interior)
CREATE TABLE IF NOT EXISTS sr_garden_plot_beds (
    bed_id UUID PRIMARY KEY,
    island_id UUID NOT NULL,
    plot_index INTEGER NOT NULL,
    bed_type VARCHAR(32) NOT NULL DEFAULT 'BASIC',
    target_crop VARCHAR(32),
    min_local_x INTEGER NOT NULL,
    min_local_z INTEGER NOT NULL,
    max_local_x INTEGER NOT NULL,
    max_local_z INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (island_id, plot_index) REFERENCES sr_garden_plots(island_id, plot_index) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_plot_beds_lookup ON sr_garden_plot_beds(island_id, plot_index);

-- 3. Saved Plot Layouts
CREATE TABLE IF NOT EXISTS sr_garden_saved_layouts (
    layout_id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    layout_name VARCHAR(64) NOT NULL,
    preset_base VARCHAR(64) NOT NULL DEFAULT 'CLEAN_CANVAS',
    irrigation_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    decoration_style VARCHAR(32) NOT NULL DEFAULT 'NONE',
    beds_serialized TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_island_saved_layout UNIQUE (island_id, layout_name)
);

CREATE INDEX IF NOT EXISTS idx_saved_layouts_island ON sr_garden_saved_layouts(island_id);

