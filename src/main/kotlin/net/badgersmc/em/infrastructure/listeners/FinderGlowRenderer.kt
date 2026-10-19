package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderBeamPlanner
import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.config.EnthusiaMarketConfig
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.floor

/** Server-thread, recipient-private displays. Hidden before world insertion; never saved to chunks. */
internal class FinderGlowRenderer(private val plugin: Plugin) {
    private data class Style(val color: Int, val width: Float)
    private data class Scene(val beams: List<FinderBeamPlanner.Beam>, val style: Style,
                             val displays: List<BlockDisplay>)
    private val scenes = mutableMapOf<UUID, Scene>()
    internal var supported: (Player) -> Boolean = { !FinderClientSupport.bedrock(it) }
    internal var spawn: (Location, Consumer<BlockDisplay>) -> BlockDisplay = { location, initialize ->
        location.world.spawn(location, BlockDisplay::class.java, initialize)
    }
    private var warned = false

    fun render(player: Player, frame: FinderTrailTracker.Frame, config: EnthusiaMarketConfig.FinderOutline): Boolean {
        val target = frame.target ?: return clearAndFalse(player.uniqueId)
        val point = player.location
        val distance = target.distance(FinderTrailTracker.Point(point.x, point.y, point.z))
        val options = FinderOutlineStyle.options(config)
        if (!enabled(player, config) || distance > options.revealDistance) {
            return clearAndFalse(player.uniqueId)
        }
        val beams = FinderBeamPlanner.plan(frame.footprint, target, options)
        val style = Style(FinderOutlineStyle.dust(config).color.asRGB(), width(config.beamWidth))
        val current = scenes[player.uniqueId]
        if (!allowed(player, beams, config)) return clearAndFalse(player.uniqueId)
        if (unchanged(current, beams, style)) return true
        clear(player.uniqueId)
        return create(player, beams, style)
    }

    private fun unchanged(scene: Scene?, beams: List<FinderBeamPlanner.Beam>, style: Style): Boolean =
        scene?.beams == beams && scene.style == style && scene.displays.all { it.isValid }

    private fun enabled(player: Player, config: EnthusiaMarketConfig.FinderOutline): Boolean =
        config.glow && config.enabled && supported(player)

    private fun allowed(player: Player, beams: List<FinderBeamPlanner.Beam>, config: EnthusiaMarketConfig.FinderOutline): Boolean {
        val existing = scenes.filterKeys { it != player.uniqueId }.values.sumOf { it.displays.size }
        return beams.isNotEmpty() && beams.size <= config.maxDisplaysPerPlayer.coerceIn(0, 128) &&
            existing + beams.size <= config.maxDisplays.coerceIn(0, 1024) && beams.all {
                loadedBeam(player, it)
            }
    }

    private fun loadedBeam(player: Player, beam: FinderBeamPlanner.Beam): Boolean {
        val length = beam.from.distance(beam.to)
        if (length !in 0.01..256.0) return false
        val xs = chunk(minOf(beam.from.x, beam.to.x))..chunk(maxOf(beam.from.x, beam.to.x))
        val zs = chunk(minOf(beam.from.z, beam.to.z))..chunk(maxOf(beam.from.z, beam.to.z))
        return xs.all { x -> zs.all { z -> player.world.isChunkLoaded(x, z) } }
    }

    private fun chunk(value: Double): Int = floor(value).toInt() shr 4

    private fun create(player: Player, beams: List<FinderBeamPlanner.Beam>, style: Style): Boolean {
        val displays = mutableListOf<BlockDisplay>()
        return try {
            beams.forEach { beam ->
                val display = spawnBeam(player, beam, style, displays)
                check(display.isValid) { "Display spawn refused" }
                player.showEntity(plugin, display)
            }
            scenes[player.uniqueId] = Scene(beams, style, displays)
            true
        } catch (error: RuntimeException) {
            displays.forEach { owned.remove(it.uniqueId); it.remove() }
            if (!warned) plugin.logger.warning("Private finder glow unavailable (${error.javaClass.simpleName}); using particles.")
            warned = true
            false
        }
    }

    private fun spawnBeam(player: Player, beam: FinderBeamPlanner.Beam, style: Style,
                          displays: MutableList<BlockDisplay>): BlockDisplay =
        spawn(Location(player.world, beam.from.x, beam.from.y, beam.from.z), Consumer {
            displays += it
            initialize(it, beam, style)
            owned += it.uniqueId
        })

    private fun initialize(display: BlockDisplay, beam: FinderBeamPlanner.Beam, style: Style) {
        display.isVisibleByDefault = false
        display.isPersistent = false
        display.setGravity(false)
        display.isInvulnerable = true
        display.block = Material.YELLOW_STAINED_GLASS.createBlockData()
        display.isGlowing = true
        display.glowColorOverride = org.bukkit.Color.fromRGB(style.color)
        display.brightness = Display.Brightness(15, 15)
        display.shadowRadius = 0f
        display.transformation = transform(beam, style.width)
    }

    private fun transform(beam: FinderBeamPlanner.Beam, width: Float): Transformation {
        val delta = Vector3f((beam.to.x - beam.from.x).toFloat(), (beam.to.y - beam.from.y).toFloat(),
            (beam.to.z - beam.from.z).toFloat())
        val length = delta.length()
        val rotation = Quaternionf().rotationTo(Vector3f(0f, 0f, 1f), Vector3f(delta).normalize())
        return Transformation(rotation.transform(Vector3f(-width / 2, -width / 2, 0f)), rotation,
            Vector3f(width, width, length), Quaternionf())
    }

    private fun width(value: Double): Float = (value.takeIf { it.isFinite() } ?: 0.04).coerceIn(0.01, 0.2).toFloat()
    private fun clearAndFalse(id: UUID): Boolean { clear(id); return false }
    fun clear(id: UUID) { scenes.remove(id)?.displays?.forEach { owned.remove(it.uniqueId); it.remove() } }
    fun retain(ids: Set<UUID>) { scenes.keys.filter { it !in ids }.forEach(::clear) }
    fun close() { scenes.keys.toList().forEach(::clear) }

    companion object {
        private val owned = mutableSetOf<UUID>()
        fun owns(id: UUID): Boolean = id in owned
    }
}
