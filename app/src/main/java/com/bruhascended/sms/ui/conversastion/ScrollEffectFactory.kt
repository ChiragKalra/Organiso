package com.bruhascended.sms.ui.conversastion

import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView



class ScrollEffectFactory : RecyclerView.EdgeEffectFactory() {

    companion object{
        /** The magnitude of rotation while the list is scrolled. */
        private const val SCROLL_ROTATION_MAGNITUDE = -0.25f

        /** The magnitude of rotation while the list is over-scrolled. */
        private const val OVERSCROLL_ROTATION_MAGNITUDE = 3.5f

        /** The magnitude of translation distance while the list is over-scrolled. */
        private const val OVERSCROLL_TRANSLATION_MAGNITUDE = 0.1f

        /** The magnitude of translation distance when the list reaches the edge on fling. */
        private const val FLING_TRANSLATION_MAGNITUDE = 0.25f

        fun RecyclerView.forEachVisibleHolder(
            action: (MessageViewHolder) -> Unit
        ) {
            for (i in 0 until childCount) {
                action(getChildViewHolder(getChildAt(i)) as MessageViewHolder)
            }
        }
    }

    class OnScrollListener: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            recyclerView.forEachVisibleHolder { holder: MessageViewHolder ->
                holder.rotation
                    .setStartVelocity(holder.currentVelocity - dx * SCROLL_ROTATION_MAGNITUDE)
                    .start()
            }
        }
    }


    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
        return object : EdgeEffect(recyclerView.context) {

            override fun onPull(deltaDistance: Float) {
                super.onPull(deltaDistance)
                handlePull(deltaDistance)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {
                super.onPull(deltaDistance, displacement)
                handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                // This is called on every touch event while the list is scrolled with a finger.
                // We simply update the view properties without animation.
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                val rotationDelta = sign * deltaDistance * OVERSCROLL_ROTATION_MAGNITUDE
                val translationYDelta =
                    sign * recyclerView.width * deltaDistance * OVERSCROLL_TRANSLATION_MAGNITUDE
                recyclerView.forEachVisibleHolder { holder: MessageViewHolder ->
                    holder.rotation.cancel()
                    holder.translationY.cancel()
                    holder.itemView.rotation += rotationDelta
                    holder.itemView.translationY += translationYDelta
                }
            }

            override fun onRelease() {
                super.onRelease()
                // The finger is lifted. This is when we should start the animations to bring
                // the view property values back to their resting states.
                recyclerView.forEachVisibleHolder { holder: MessageViewHolder ->
                    holder.rotation.start()
                    holder.translationY.start()
                }
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                // The list has reached the edge on fling.
                val translationVelocity = sign * velocity * FLING_TRANSLATION_MAGNITUDE
                recyclerView.forEachVisibleHolder { holder: MessageViewHolder ->
                    holder.translationY
                        .setStartVelocity(translationVelocity)
                        .start()
                }
            }
        }
    }
}
