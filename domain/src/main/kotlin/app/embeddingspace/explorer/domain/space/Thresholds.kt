package app.embeddingspace.explorer.domain.space

import app.embeddingspace.explorer.domain.model.MediaType

/**
 * When is a new item close enough to join an existing cluster? Centred cosine, tuned in the spike.
 * Audio is the weak modality, so anything involving audio gets a lower bar and a tentative mark.
 */
data class JoinThresholds(
    val imageLike: Float = 0.30f,
    val textText: Float = 0.45f,
    val audio: Float = 0.20f,
) {
    fun forPair(a: MediaType, b: MediaType): Float {
        if (a == MediaType.AUDIO || b == MediaType.AUDIO) return audio
        if (a == MediaType.TEXT && b == MediaType.TEXT) return textText
        return imageLike
    }

    /** Shifts every threshold by [delta], for a future sensitivity setting. */
    fun shifted(delta: Float) = JoinThresholds(imageLike + delta, textText + delta, audio + delta)
}

/** Tunable knobs of the space. The defaults are the spike's recommendations. */
data class SpaceConfig(
    val thresholds: JoinThresholds = JoinThresholds(),
    val placeK: Int = 5,
    val placeTau: Float = 0.05f,
    /**
     * A photo or note is only pulled into a cluster by an audio neighbour if that audio match is this much
     * stronger than the audio bar. Audio links are weak, and on the spike vectors this keeps new topics from
     * being swallowed by whatever sound happens to be nearest.
     */
    val audioEvidenceMargin: Float = 0.10f,
    /** Rebuild the means from scratch every this many additions. */
    val meansRecomputeEvery: Int = 50,
    /** Refit the full layout every this many additions. */
    val relayoutEvery: Int = 25,
)
