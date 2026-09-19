package com.example.ui.util

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import eightbitlab.com.blurview.BlurAlgorithm
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur

/**
 * Utility for authentic Apple iOS "Liquid Glass" / VisionOS frosted blur effects using BlurView.
 */
object GlassBlurUtils {

    const val DEFAULT_BLUR_RADIUS = 20f

    /**
     * Initializes a BlurView attached to the root view with hardware-accelerated optical blur,
     * specular rounded-corner outline clipping, and window background clearing.
     */
    fun setupBlurView(
        blurView: BlurView,
        rootView: ViewGroup,
        windowBackground: Drawable? = null,
        blurRadius: Float = DEFAULT_BLUR_RADIUS,
        overlayColor: Int? = null
    ) {
        // Enforce smooth corner clipping for the specular glass drawable outline
        blurView.outlineProvider = ViewOutlineProvider.BACKGROUND
        blurView.clipToOutline = true

        val algorithm: BlurAlgorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffectBlur()
        } else {
            RenderScriptBlur(blurView.context)
        }

        val facade = blurView.setupWith(rootView, algorithm)
            .setBlurRadius(blurRadius.coerceIn(4f, 25f))
            .setBlurAutoUpdate(true)

        if (windowBackground != null) {
            facade.setFrameClearDrawable(windowBackground)
        }

        if (overlayColor != null) {
            facade.setOverlayColor(overlayColor)
        }
    }

    /**
     * Helper to set up multiple BlurViews on an Activity's root decor view.
     */
    fun setupActivityGlass(
        activity: Activity,
        vararg blurViews: BlurView,
        blurRadius: Float = DEFAULT_BLUR_RADIUS
    ) {
        val decorView = activity.window.decorView
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content) ?: (decorView as ViewGroup)
        val windowBackground = decorView.background

        for (bv in blurViews) {
            setupBlurView(
                blurView = bv,
                rootView = rootView,
                windowBackground = windowBackground,
                blurRadius = blurRadius
            )
        }

        // Attach lifecycle-aware pause/resume if activity is a LifecycleOwner to eliminate idle battery drain
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    for (bv in blurViews) {
                        bv.setBlurAutoUpdate(true)
                    }
                }

                override fun onPause(owner: LifecycleOwner) {
                    for (bv in blurViews) {
                        bv.setBlurAutoUpdate(false)
                    }
                }
            })
        }
    }
}
