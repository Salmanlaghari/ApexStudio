/*
 * Vendored from https://github.com/gauravk95/audio-visualizer-android
 * (Apache License 2.0, Copyright 2018 Gaurav Kumar).
 * Changes for ApexStudio: package renamed, android.support -> androidx,
 * XML attributes replaced with programmatic setters (BaseVisualizer).
 */
/*
        Copyright 2018 Gaurav Kumar

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
*/
package com.apexstudio.app.vendor.visualizer.base;

import android.content.Context;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.apexstudio.app.vendor.visualizer.utils.AVConstants;
import com.apexstudio.app.vendor.visualizer.model.AnimSpeed;
import com.apexstudio.app.vendor.visualizer.model.PaintStyle;
import com.apexstudio.app.vendor.visualizer.model.PositionGravity;

/**
 * Base class for the visualizers
 * <p>
 * Created by gk
 */

abstract public class BaseVisualizer extends View {

    protected byte[] mRawAudioBytes;
    protected Paint mPaint;
    protected Visualizer mVisualizer;
    protected int mColor = AVConstants.DEFAULT_COLOR;

    protected PaintStyle mPaintStyle = PaintStyle.FILL;
    protected PositionGravity mPositionGravity = PositionGravity.BOTTOM;

    protected float mStrokeWidth = AVConstants.DEFAULT_STROKE_WIDTH;
    protected float mDensity = AVConstants.DEFAULT_DENSITY;

    protected AnimSpeed mAnimSpeed = AnimSpeed.MEDIUM;
    protected boolean isVisualizationEnabled = true;

    public BaseVisualizer(Context context) {
        super(context);
        init(context, null);
        init();
    }

    public BaseVisualizer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
        init();
    }

    public BaseVisualizer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
        init();
    }

    private void init(Context context, AttributeSet attrs) {
        // Phase 4 (ApexStudio): vendored from gauravk95/audio-visualizer-android
        // (Apache-2.0). XML attributes stripped — configure programmatically
        // via the setters below.
        this.mDensity = AVConstants.DEFAULT_DENSITY;
        this.mColor = AVConstants.DEFAULT_COLOR;
        this.mStrokeWidth = AVConstants.DEFAULT_STROKE_WIDTH;
        this.mPaintStyle = PaintStyle.FILL;
        this.mPositionGravity = PositionGravity.BOTTOM;
        this.mAnimSpeed = AnimSpeed.MEDIUM;

        mPaint = new Paint();
        mPaint.setColor(mColor);
        mPaint.setStrokeWidth(mStrokeWidth);
        if (mPaintStyle == PaintStyle.FILL)
            mPaint.setStyle(Paint.Style.FILL);
        else {
            mPaint.setStyle(Paint.Style.STROKE);
        }
    }

    /**
     * Set color to visualizer with color resource id.
     *
     * @param color color resource id.
     */
    public void setColor(int color) {
        this.mColor = color;
        this.mPaint.setColor(this.mColor);
    }

    /**
     * Set the density of the visualizer
     *
     * @param density density for visualization
     */
    public void setDensity(float density) {
        //TODO: Check dynamic density change, may cause crash
        synchronized (this) {
            this.mDensity = density;
            init();
        }
    }

    /**
     * Sets the paint style of the visualizer
     *
     * @param paintStyle style of the visualizer.
     */
    public void setPaintStyle(PaintStyle paintStyle) {
        this.mPaintStyle = paintStyle;
        this.mPaint.setStyle(paintStyle == PaintStyle.FILL ? Paint.Style.FILL : Paint.Style.STROKE);
    }

    /**
     * Sets the position of the Visualization{@link PositionGravity}
     *
     * @param positionGravity position of the Visualization
     */
    public void setPositionGravity(PositionGravity positionGravity) {
        this.mPositionGravity = positionGravity;
    }

    /**
     * Sets the Animation speed of the visualization{@link AnimSpeed}
     *
     * @param animSpeed speed of the animation
     */
    public void setAnimationSpeed(AnimSpeed animSpeed) {
        this.mAnimSpeed = animSpeed;
    }

    /**
     * Sets the width of the outline {@link PaintStyle}
     *
     * @param width style of the visualizer.
     */
    public void setStrokeWidth(float width) {
        this.mStrokeWidth = width;
        this.mPaint.setStrokeWidth(width);
    }

    /**
     * Sets the audio bytes to be visualized form {@link Visualizer} or other sources
     *
     * @param bytes of the raw bytes of music
     */
    public void setRawAudioBytes(byte[] bytes) {
        this.mRawAudioBytes = bytes;
        this.invalidate();
    }

    /**
     * Sets the audio session id for the currently playing audio
     *
     * @param audioSessionId of the media to be visualised
     */
    public void setAudioSessionId(int audioSessionId) {
        if (mVisualizer != null)
            release();

        mVisualizer = new Visualizer(audioSessionId);
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

        mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                                              int samplingRate) {
                BaseVisualizer.this.mRawAudioBytes = bytes;
                invalidate();
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] bytes,
                                         int samplingRate) {
            }
        }, Visualizer.getMaxCaptureRate() / 2, true, false);

        mVisualizer.setEnabled(true);
    }

    /**
     * Releases the visualizer
     */
    public void release() {
        if (mVisualizer != null)
            mVisualizer.release();
    }

    /**
     * Enable Visualization
     */
    public void show() {
        this.isVisualizationEnabled = true;
    }

    /**
     * Disable Visualization
     */
    public void hide() {
        this.isVisualizationEnabled = false;
    }

    protected abstract void init();

}