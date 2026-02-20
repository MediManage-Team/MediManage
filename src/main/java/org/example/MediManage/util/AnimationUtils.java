package org.example.MediManage.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Centralised animation helpers for consistent Tokyo Night transitions.
 * <p>
 * Use these in controller {@code initialize()} methods:
 * 
 * <pre>
 * AnimationUtils.fadeIn(panel, 300);
 * AnimationUtils.slideInFromLeft(sidebar, 350, 30);
 * AnimationUtils.hoverScale(button, 1.03);
 * </pre>
 */
public final class AnimationUtils {

    private AnimationUtils() {
        /* utility */ }

    /* ── Fade ─────────────────────────────────── */

    /** Fade a node in (opacity 0 → 1). */
    public static void fadeIn(Node node, double ms) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
    }

    /** Fade a node out (opacity 1 → 0). */
    public static void fadeOut(Node node, double ms, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(node.getOpacity());
        ft.setToValue(0);
        ft.setInterpolator(Interpolator.EASE_IN);
        if (onFinished != null)
            ft.setOnFinished(e -> onFinished.run());
        ft.play();
    }

    /* ── Slide ────────────────────────────────── */

    /** Slide a node in from the left. */
    public static void slideInFromLeft(Node node, double ms, double distance) {
        node.setTranslateX(-distance);
        node.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromX(-distance);
        tt.setToX(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.play();
    }

    /** Slide a node in from the bottom. */
    public static void slideInFromBottom(Node node, double ms, double distance) {
        node.setTranslateY(distance);
        node.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(distance);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.play();
    }

    /* ── Hover scale ──────────────────────────── */

    /**
     * Add a scale-up / scale-down effect on mouse enter / exit.
     * Typical usage: {@code hoverScale(btn, 1.04)} for buttons,
     * or {@code hoverScale(card, 1.02)} for cards.
     */
    public static void hoverScale(Node node, double targetScale) {
        node.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
            st.setToX(targetScale);
            st.setToY(targetScale);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        node.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
    }

    /* ── Pulse / glow ─────────────────────────── */

    /**
     * Quick "pop" pulse for notifications or newly-added elements.
     */
    public static void pulse(Node node) {
        ScaleTransition up = new ScaleTransition(Duration.millis(120), node);
        up.setToX(1.08);
        up.setToY(1.08);
        up.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition down = new ScaleTransition(Duration.millis(120), node);
        down.setToX(1.0);
        down.setToY(1.0);
        down.setInterpolator(Interpolator.EASE_IN);

        SequentialTransition seq = new SequentialTransition(up, down);
        seq.play();
    }

    /**
     * Cross-fade between two nodes (old fades out, new fades in).
     * Useful for view transitions in the content area.
     */
    public static void crossFade(Node outNode, Node inNode, double ms) {
        if (outNode != null) {
            FadeTransition ftOut = new FadeTransition(Duration.millis(ms / 2), outNode);
            ftOut.setFromValue(1);
            ftOut.setToValue(0);
            ftOut.setInterpolator(Interpolator.EASE_IN);
            ftOut.play();
        }

        inNode.setOpacity(0);
        FadeTransition ftIn = new FadeTransition(Duration.millis(ms), inNode);
        ftIn.setFromValue(0);
        ftIn.setToValue(1);
        ftIn.setInterpolator(Interpolator.EASE_OUT);
        ftIn.setDelay(Duration.millis(ms / 4));
        ftIn.play();
    }
}
