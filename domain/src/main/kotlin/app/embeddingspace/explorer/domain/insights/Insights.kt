package app.embeddingspace.explorer.domain.insights

import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.space.Space

/** A neighbour of another media type. [likely] marks links that involve audio, which are only suggestions. */
data class CrossLink(val index: Int, val type: MediaType, val sim: Float, val likely: Boolean)

/** Links from one item to the closest items of the other media types. Similarities are centred cosine. */
object Insights {
    fun crossLinks(space: Space, index: Int, perType: Int = 3): List<CrossLink> {
        val own = space.entries[index].type
        val out = ArrayList<CrossLink>()
        for ((type, hits) in space.neighborsByOtherType(index, perType)) {
            for (h in hits) out.add(CrossLink(h.index, type, h.sim, likely = own == MediaType.AUDIO || type == MediaType.AUDIO))
        }
        return out
    }
}
