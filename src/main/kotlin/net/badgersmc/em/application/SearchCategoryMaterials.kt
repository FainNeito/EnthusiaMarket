package net.badgersmc.em.application

import org.bukkit.Material

/** Explicit families avoid interpreting display names or incidental substrings as categories. */
internal object SearchCategoryMaterials {
    fun matches(category: SearchCategory, material: Material): Boolean =
        matchers.getValue(category)(material)

    private fun tools(name: String) = equipment(name, TOOL_SUFFIXES, TOOL_ITEMS)
    private fun weapons(name: String) = equipment(name, WEAPON_SUFFIXES, WEAPON_ITEMS)
    private fun armor(name: String) = equipment(name, ARMOR_SUFFIXES, ARMOR_ITEMS)
    private fun equipment(name: String, suffixes: Set<String>, items: Set<String>) =
        name in items || suffixes.any(name::endsWith)

    private fun wood(name: String): Boolean {
        val unstripped = name.removePrefix("STRIPPED_")
        return WOOD_FAMILIES.any { family ->
            unstripped.removePrefix("${family}_").takeIf { unstripped.startsWith("${family}_") } in WOOD_PARTS
        }
    }

    private fun foliage(name: String) = SearchBuildingCategories.isFlower(name) ||
        equipment(name, FOLIAGE_SUFFIXES, FOLIAGE_ITEMS)

    private fun building(material: Material) = material.isBlock && buildingName(material.name)

    private fun buildingName(name: String) = SearchBuildingCategories.isStone(name) || wood(name) ||
        equipment(name, BUILDING_SUFFIXES, BUILDING_ITEMS) || name.endsWith("COPPER") || "COPPER_" in name

    private val matchers: Map<SearchCategory, (Material) -> Boolean> = mapOf(
        SearchCategory.TOOLS to { tools(it.name) },
        SearchCategory.WEAPONS to { weapons(it.name) },
        SearchCategory.ARMOR to { armor(it.name) },
        SearchCategory.COMBAT to { weapons(it.name) || armor(it.name) || it.name in COMBAT_ITEMS },
        SearchCategory.FLOWERS to { SearchBuildingCategories.isFlower(it.name) },
        SearchCategory.STONE to { SearchBuildingCategories.isStone(it.name) },
        SearchCategory.FOLIAGE to { foliage(it.name) },
        SearchCategory.WOOD to { wood(it.name) },
        SearchCategory.REDSTONE to { equipment(it.name, REDSTONE_SUFFIXES, REDSTONE_ITEMS) },
        SearchCategory.BUILDING to { building(it) },
        SearchCategory.DECORATION to { it.name in DECORATION_ITEMS || equipment(it.name, DECORATION_SUFFIXES, LIGHT_ITEMS) || SearchBuildingCategories.isFlower(it.name) },
        SearchCategory.FOOD to { it.isEdible },
        SearchCategory.FARMING to { equipment(it.name, FARM_SUFFIXES, FARM_ITEMS) },
        SearchCategory.ORES to { it.name.endsWith("_ORE") || it.name in LEGACY_ORE_ITEMS || it.name == "ANCIENT_DEBRIS" },
        SearchCategory.ORE_BLOCKS to { it.name.endsWith("_ORE") || it.name == "ANCIENT_DEBRIS" },
        SearchCategory.MATERIALS to { it.name in MATERIAL_ITEMS || it.name.endsWith("_INGOT") || it.name.endsWith("_NUGGET") },
        SearchCategory.POTIONS to { it.name in POTION_ITEMS },
        SearchCategory.BREWING to { it.name in POTION_ITEMS || it.name in BREWING_ITEMS },
        SearchCategory.ENCHANTING to { it.name in ENCHANTING_ITEMS },
        SearchCategory.STORAGE to { equipment(it.name, STORAGE_SUFFIXES, STORAGE_ITEMS) },
        SearchCategory.SHULKER to { it.name == "SHULKER_BOX" || it.name.endsWith("_SHULKER_BOX") },
        SearchCategory.TRANSPORT to { equipment(it.name, TRANSPORT_SUFFIXES, TRANSPORT_ITEMS) },
        SearchCategory.LIGHTING to { equipment(it.name, LIGHT_SUFFIXES, LIGHT_ITEMS) },
        SearchCategory.WORKSTATIONS to { it.name in WORKSTATION_ITEMS },
        SearchCategory.DROPS to { it.name in DROP_ITEMS },
    )

    private val TOOL_SUFFIXES = setOf("_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD")
    private val TOOL_ITEMS = setOf("SHEARS", "FISHING_ROD", "FLINT_AND_STEEL", "BRUSH", "SPYGLASS", "COMPASS", "RECOVERY_COMPASS", "CLOCK")
    private val WEAPON_SUFFIXES = setOf("_SWORD", "_AXE", "_SPEAR")
    private val WEAPON_ITEMS = setOf("BOW", "CROSSBOW", "TRIDENT", "MACE", "SPEAR")
    private val ARMOR_SUFFIXES = setOf("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR")
    private val ARMOR_ITEMS = setOf("ELYTRA", "WOLF_ARMOR")
    private val COMBAT_ITEMS = setOf("SHIELD", "TOTEM_OF_UNDYING", "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW", "FIREWORK_ROCKET", "WIND_CHARGE")
    private val WOOD_FAMILIES = setOf("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED")
    private val WOOD_PARTS = setOf("LOG", "WOOD", "STEM", "HYPHAE", "PLANKS", "BLOCK", "MOSAIC", "STAIRS", "SLAB", "MOSAIC_STAIRS", "MOSAIC_SLAB", "FENCE", "FENCE_GATE", "DOOR", "TRAPDOOR", "BUTTON", "PRESSURE_PLATE", "SIGN", "WALL_SIGN", "HANGING_SIGN", "WALL_HANGING_SIGN")
    private val FOLIAGE_SUFFIXES = setOf("_LEAVES", "_SAPLING", "_FUNGUS", "_MUSHROOM", "_MUSHROOM_BLOCK", "_VINES", "_VINES_PLANT", "_ROOTS", "_LEAF", "_LILY")
    private val FOLIAGE_ITEMS = setOf("VINE", "MOSS_BLOCK", "MOSS_CARPET", "PALE_MOSS_BLOCK", "PALE_MOSS_CARPET", "PALE_HANGING_MOSS", "AZALEA", "FERN", "LARGE_FERN", "SHORT_GRASS", "TALL_GRASS", "DEAD_BUSH", "SEAGRASS", "KELP", "CACTUS", "BAMBOO", "MANGROVE_PROPAGULE", "LEAF_LITTER", "BUSH", "FIREFLY_BUSH", "HANGING_ROOTS", "MUSHROOM_STEM")
    private val BUILDING_SUFFIXES = setOf("_CONCRETE", "_CONCRETE_POWDER", "_TERRACOTTA", "_GLAZED_TERRACOTTA", "_WOOL", "_GLASS", "_GLASS_PANE", "_BRICKS", "_SLAB", "_STAIRS", "_WALL", "_CORAL_BLOCK")
    private val BUILDING_ITEMS = setOf("BRICKS", "MUD_BRICKS", "PACKED_MUD", "SANDSTONE", "CHISELED_SANDSTONE", "CUT_SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE", "CHISELED_RED_SANDSTONE", "CUT_RED_SANDSTONE", "SMOOTH_RED_SANDSTONE", "QUARTZ_BLOCK", "QUARTZ_PILLAR", "CHISELED_QUARTZ_BLOCK", "SMOOTH_QUARTZ", "PRISMARINE", "PRISMARINE_BRICKS", "DARK_PRISMARINE", "PURPUR_BLOCK", "PURPUR_PILLAR", "TERRACOTTA", "GLASS", "GLASS_PANE", "OBSIDIAN", "CRYING_OBSIDIAN", "NETHERRACK", "NETHER_BRICKS", "RED_NETHER_BRICKS", "BONE_BLOCK", "SNOW_BLOCK", "ICE", "PACKED_ICE", "BLUE_ICE", "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK", "MUD", "SAND", "RED_SAND", "GRAVEL", "CLAY")
    private val REDSTONE_SUFFIXES = setOf("_BUTTON", "_PRESSURE_PLATE", "_COPPER_BULB")
    private val REDSTONE_ITEMS = setOf("REDSTONE", "REDSTONE_TORCH", "REDSTONE_BLOCK", "REDSTONE_LAMP", "REPEATER", "COMPARATOR", "OBSERVER", "PISTON", "STICKY_PISTON", "DISPENSER", "DROPPER", "HOPPER", "DAYLIGHT_DETECTOR", "LECTERN", "TARGET", "LEVER", "TRIPWIRE_HOOK", "STRING", "NOTE_BLOCK", "TNT", "CRAFTER", "TRAPPED_CHEST", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "IRON_DOOR", "IRON_TRAPDOOR", "COPPER_BULB")
    private val POTION_ITEMS = setOf("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW")
    private val LEGACY_ORE_ITEMS = setOf("COAL", "RAW_IRON", "RAW_COPPER", "RAW_GOLD", "IRON_INGOT", "COPPER_INGOT", "GOLD_INGOT", "GOLD_NUGGET", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "REDSTONE", "NETHER_QUARTZ")
    private val MATERIAL_ITEMS = LEGACY_ORE_ITEMS + setOf("QUARTZ", "CHARCOAL", "AMETHYST_SHARD", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "NETHERITE_SCRAP", "FLINT", "CLAY_BALL", "BRICK", "NETHER_BRICK", "STICK", "PAPER", "LEATHER", "STRING", "RESIN_CLUMP", "RESIN_BRICK", "ECHO_SHARD")
    private val BREWING_ITEMS = setOf("BREWING_STAND", "CAULDRON", "GLASS_BOTTLE", "BLAZE_POWDER", "NETHER_WART", "FERMENTED_SPIDER_EYE", "SPIDER_EYE", "SUGAR", "RABBIT_FOOT", "GLISTERING_MELON_SLICE", "GOLDEN_CARROT", "MAGMA_CREAM", "GHAST_TEAR", "PHANTOM_MEMBRANE", "PUFFERFISH", "GUNPOWDER", "REDSTONE", "GLOWSTONE_DUST", "DRAGON_BREATH", "TURTLE_HELMET", "STONE", "COBWEB", "SLIME_BLOCK", "BREEZE_ROD")
    private val ENCHANTING_ITEMS = setOf("ENCHANTED_BOOK", "BOOK", "ENCHANTING_TABLE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "GRINDSTONE", "LAPIS_LAZULI", "EXPERIENCE_BOTTLE", "BOOKSHELF", "CHISELED_BOOKSHELF")
    private val STORAGE_SUFFIXES = setOf("_SHULKER_BOX", "_BUNDLE", "_COPPER_CHEST")
    private val STORAGE_ITEMS = setOf("CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX", "BUNDLE", "ENDER_CHEST", "COPPER_CHEST")
    private val TRANSPORT_SUFFIXES = setOf("_BOAT", "_RAFT", "_MINECART", "_HARNESS")
    private val TRANSPORT_ITEMS = setOf("MINECART", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL", "SADDLE", "ELYTRA", "FIREWORK_ROCKET", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "LEAD")
    private val LIGHT_SUFFIXES = setOf("_CANDLE", "_COPPER_LANTERN", "_COPPER_TORCH", "_COPPER_BULB")
    private val LIGHT_ITEMS = setOf("TORCH", "SOUL_TORCH", "LANTERN", "SOUL_LANTERN", "GLOWSTONE", "SEA_LANTERN", "CANDLE", "REDSTONE_LAMP", "SHROOMLIGHT", "JACK_O_LANTERN", "END_ROD", "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "CAMPFIRE", "SOUL_CAMPFIRE", "GLOW_LICHEN", "COPPER_BULB", "COPPER_TORCH", "COPPER_LANTERN")
    private val WORKSTATION_ITEMS = setOf("CRAFTING_TABLE", "FURNACE", "BLAST_FURNACE", "SMOKER", "STONECUTTER", "LOOM", "SMITHING_TABLE", "FLETCHING_TABLE", "CARTOGRAPHY_TABLE", "LECTERN", "BREWING_STAND", "CAULDRON", "COMPOSTER", "GRINDSTONE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "ENCHANTING_TABLE", "CRAFTER")
    private val DECORATION_SUFFIXES = setOf("_BANNER", "_CARPET", "_BED", "_HEAD", "_SKULL", "_POTTERY_SHERD", "_CANDLE", "_COPPER_LANTERN", "_COPPER_TORCH")
    private val DECORATION_ITEMS = setOf("PAINTING", "ITEM_FRAME", "GLOW_ITEM_FRAME", "FLOWER_POT", "DECORATED_POT", "ARMOR_STAND", "BELL", "CHAIN", "IRON_CHAIN", "COPPER_CHAIN", "END_CRYSTAL")
    private val FARM_SUFFIXES = setOf("_SEEDS", "_POD")
    private val FARM_ITEMS = setOf("WHEAT", "CARROT", "POTATO", "BEETROOT", "SUGAR_CANE", "BONE_MEAL", "PUMPKIN", "MELON", "COCOA_BEANS", "SWEET_BERRIES", "GLOW_BERRIES", "NETHER_WART", "BAMBOO", "CACTUS", "KELP", "EGG", "HONEY_BOTTLE", "HONEYCOMB", "COMPOSTER")
    private val DROP_ITEMS = setOf("ROTTEN_FLESH", "BONE", "STRING", "GUNPOWDER", "ENDER_PEARL", "BLAZE_ROD", "SLIME_BALL", "LEATHER", "FEATHER", "SPIDER_EYE", "GHAST_TEAR", "MAGMA_CREAM", "PHANTOM_MEMBRANE", "RABBIT_HIDE", "RABBIT_FOOT", "INK_SAC", "GLOW_INK_SAC", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL", "SHULKER_SHELL", "BREEZE_ROD", "ARMADILLO_SCUTE", "TURTLE_SCUTE")
}
