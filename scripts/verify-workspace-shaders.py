#!/usr/bin/env python3
"""Compile/render the production workspace GLSL on host Mesa EGL, NOT an Android/device test.
Requires numpy and PyOpenGL. No screenshots/assets/models/network are used.
"""
import ctypes
import json
import os
from pathlib import Path
import re
import textwrap

os.environ.setdefault("PYOPENGL_PLATFORM", "egl")
os.environ.setdefault("EGL_PLATFORM", "surfaceless")
import numpy as np
from OpenGL import EGL as egl
from OpenGL import GLES2 as gl


def run():
    root = Path(__file__).resolve().parents[1]
    source = (root / "app/src/main/java/com/apexstudio/app/data/effect/WorkspaceEffectGlEffect.kt").read_text()
    vertex = textwrap.dedent(re.search(r'private val VERTEX_SHADER = """(.*?)"""', source, re.S).group(1)).strip()
    fragment = textwrap.dedent(re.search(r'fun fragmentShaderSource\(\): String = """(.*?)"""', source, re.S).group(1)).strip()
    display = egl.eglGetDisplay(egl.EGL_DEFAULT_DISPLAY)
    major, minor = egl.EGLint(), egl.EGLint()
    assert egl.eglInitialize(display, major, minor), "EGL initialization failed"
    attrs = (egl.EGLint * 15)(egl.EGL_SURFACE_TYPE, egl.EGL_PBUFFER_BIT,
        egl.EGL_RENDERABLE_TYPE, egl.EGL_OPENGL_ES2_BIT, egl.EGL_RED_SIZE, 8,
        egl.EGL_GREEN_SIZE, 8, egl.EGL_BLUE_SIZE, 8, egl.EGL_ALPHA_SIZE, 8, egl.EGL_NONE, 0, 0)
    configs, count = (egl.EGLConfig * 1)(), egl.EGLint()
    assert egl.eglChooseConfig(display, attrs, configs, 1, count) and count.value
    width, height = 96, 64
    surface_attrs = (egl.EGLint * 5)(egl.EGL_WIDTH, width, egl.EGL_HEIGHT, height, egl.EGL_NONE)
    surface = egl.eglCreatePbufferSurface(display, configs[0], surface_attrs)
    context_attrs = (egl.EGLint * 3)(egl.EGL_CONTEXT_CLIENT_VERSION, 2, egl.EGL_NONE)
    egl.eglBindAPI(egl.EGL_OPENGL_ES_API)
    context = egl.eglCreateContext(display, configs[0], egl.EGL_NO_CONTEXT, context_attrs)
    assert egl.eglMakeCurrent(display, surface, surface, context)
    try:
        def compile_shader(kind, text):
            shader = gl.glCreateShader(kind)
            gl.glShaderSource(shader, text)
            gl.glCompileShader(shader)
            assert gl.glGetShaderiv(shader, gl.GL_COMPILE_STATUS), gl.glGetShaderInfoLog(shader)
            return shader
        program = gl.glCreateProgram()
        for kind, text in [(gl.GL_VERTEX_SHADER, vertex), (gl.GL_FRAGMENT_SHADER, fragment)]:
            gl.glAttachShader(program, compile_shader(kind, text))
        gl.glLinkProgram(program)
        assert gl.glGetProgramiv(program, gl.GL_LINK_STATUS), gl.glGetProgramInfoLog(program)
        gl.glUseProgram(program)
        positions = np.array([[-1, -1, 0, 1], [1, -1, 0, 1], [-1, 1, 0, 1], [1, 1, 0, 1]], dtype=np.float32)
        buffer = gl.glGenBuffers(1)
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, buffer)
        gl.glBufferData(gl.GL_ARRAY_BUFFER, positions.nbytes, positions, gl.GL_STATIC_DRAW)
        attribute = gl.glGetAttribLocation(program, "aFramePosition")
        gl.glVertexAttribPointer(attribute, 4, gl.GL_FLOAT, False, 0, ctypes.c_void_p(0))
        gl.glEnableVertexAttribArray(attribute)
        identity = np.identity(4, dtype=np.float32)
        tex_transform = np.array([[.5, 0, 0, 0], [0, .5, 0, 0], [0, 0, 1, 0], [.5, .5, 0, 1]], dtype=np.float32)
        for name, matrix in [("uTransformationMatrix", identity), ("uTexTransformationMatrix", tex_transform)]:
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(program, name), 1, False, matrix)
        y, x = np.indices((height, width))
        pixels = np.stack([(x * 255 // (width - 1)), (y * 255 // (height - 1)), ((x // 8 + y // 8) % 2) * 160 + 40, np.full_like(x, 255)], axis=-1).astype(np.uint8)
        texture = gl.glGenTextures(1)
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, texture)
        for parameter in [gl.GL_TEXTURE_MIN_FILTER, gl.GL_TEXTURE_MAG_FILTER]:
            gl.glTexParameteri(gl.GL_TEXTURE_2D, parameter, gl.GL_NEAREST)
        for parameter in [gl.GL_TEXTURE_WRAP_S, gl.GL_TEXTURE_WRAP_T]:
            gl.glTexParameteri(gl.GL_TEXTURE_2D, parameter, gl.GL_CLAMP_TO_EDGE)
        gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, width, height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, pixels)
        gl.glUniform1i(gl.glGetUniformLocation(program, "uTexSampler"), 0)
        gl.glUniform2f(gl.glGetUniformLocation(program, "uTexel"), 1 / width, 1 / height)
        gl.glUniform1f(gl.glGetUniformLocation(program, "uAspect"), width / height)
        gl.glViewport(0, 0, width, height)
        def render(kind, enabled=1., intensity=1., elapsed=.08, exposure=0., contrast=1., saturation=1.):
            for name, value in [("uKind", kind), ("uEnabled", enabled), ("uIntensity", intensity), ("uElapsed", elapsed), ("uExposure", exposure), ("uContrast", contrast), ("uSaturation", saturation)]:
                gl.glUniform1f(gl.glGetUniformLocation(program, name), value)
            gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
            gl.glFinish()
            output = np.empty_like(pixels)
            gl.glReadPixels(0, 0, width, height, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, output)
            assert gl.glGetError() == gl.GL_NO_ERROR
            return output
        result = []
        filter_outputs = set()
        for kind, name in enumerate(["mono", "warm", "pixelate", "split_2", "split_3", "cartoon", "bubble", "hearts", "heartbeat", "ice", "soft_warm", "cool", "faded", "amber", "rose", "mint", "teal_orange", "crisp", "matte", "bold_mono", "sepia", "lavender"]):
            assert np.array_equal(render(kind, enabled=0), pixels), f"{name}: disabled effect modified pixels"
            assert np.array_equal(render(kind, intensity=0), pixels), f"{name}: zero intensity modified pixels"
            output = render(kind)
            changed = int(np.count_nonzero(np.any(output[:, :, :3] != pixels[:, :, :3], axis=-1)))
            assert changed > 0, f"{name}: enabled effect did not edit any pixels"
            assert np.array_equal(output[:, :, 3], pixels[:, :, 3]), f"{name}: alpha changed"
            if kind >= 10:
                filter_outputs.add(output.tobytes())
            animated = kind in [6, 7, 8]
            if animated:
                assert not np.array_equal(output, render(kind, elapsed=.5)), f"{name}: time uniform did not animate"
            result.append({"preset": name, "changed_pixels": changed, "disabled_and_zero_intensity_identity": True, "animation_checked": animated})
        assert len(filter_outputs) == 12, "Every original color look must produce distinct output pixels"
        custom_checks = []
        baseline = render(10)
        for parameter, value in [("exposure", .4), ("contrast", 1.4), ("saturation", 0.)]:
            kwargs = {parameter: value}
            assert not np.array_equal(baseline, render(10, **kwargs)), f"{parameter} did not change actual pixels"
            assert np.array_equal(pixels, render(10, enabled=0, **kwargs)), f"{parameter} leaked outside active range"
            assert np.array_equal(pixels, render(10, intensity=0, **kwargs)), f"{parameter} leaked at zero strength"
            custom_checks.append({"parameter": parameter, "changes_pixels_and_respects_disabled_range": True})
        alpha_results = []
        for shader_name in ["StickerGlEffect", "TextOverlayGlEffect"]:
            overlay_source = (root / f"app/src/main/java/com/apexstudio/app/data/effect/{shader_name}.kt").read_text()
            overlay_fragment = textwrap.dedent(re.search(r'private val FRAGMENT_SHADER = """(.*?)"""', overlay_source, re.S).group(1)).strip()
            overlay_program = gl.glCreateProgram()
            for kind, text in [(gl.GL_VERTEX_SHADER, vertex), (gl.GL_FRAGMENT_SHADER, overlay_fragment)]:
                gl.glAttachShader(overlay_program, compile_shader(kind, text))
            gl.glLinkProgram(overlay_program)
            assert gl.glGetProgramiv(overlay_program, gl.GL_LINK_STATUS), gl.glGetProgramInfoLog(overlay_program)
            gl.glUseProgram(overlay_program)
            attribute = gl.glGetAttribLocation(overlay_program, "aFramePosition")
            gl.glVertexAttribPointer(attribute, 4, gl.GL_FLOAT, False, 0, ctypes.c_void_p(0))
            gl.glEnableVertexAttribArray(attribute)
            for name, matrix in [("uTransformationMatrix", identity), ("uTexTransformationMatrix", tex_transform)]:
                gl.glUniformMatrix4fv(gl.glGetUniformLocation(overlay_program, name), 1, False, matrix)
            gl.glUniform1i(gl.glGetUniformLocation(overlay_program, "uTexSampler"), 0)
            gl.glUniform1i(gl.glGetUniformLocation(overlay_program, "uOverlaySampler"), 1)
            gl.glUniform1f(gl.glGetUniformLocation(overlay_program, "uOverlayEnabled"), 1.)
            overlay_texture = gl.glGenTextures(1)
            gl.glActiveTexture(gl.GL_TEXTURE1)
            gl.glBindTexture(gl.GL_TEXTURE_2D, overlay_texture)
            for parameter in [gl.GL_TEXTURE_MIN_FILTER, gl.GL_TEXTURE_MAG_FILTER]:
                gl.glTexParameteri(gl.GL_TEXTURE_2D, parameter, gl.GL_NEAREST)
            for rgba in [(0, 0, 0, 0), (128, 0, 0, 128), (0, 255, 0, 255)]:
                overlay_pixels = np.full_like(pixels, rgba)
                gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, width, height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, overlay_pixels)
                gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
                gl.glFinish()
                output = np.empty_like(pixels)
                gl.glReadPixels(0, 0, width, height, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, output)
                expected = np.array(rgba[:3]) + pixels[:, :, :3].astype(float) * (1. - rgba[3] / 255.)
                error = np.abs(output[:, :, :3].astype(float) - np.rint(expected))
                assert error.max() <= 1., f"{shader_name}: premultiplied-alpha mismatch {error.max()}"
                assert np.array_equal(output[:, :, 3], pixels[:, :, 3])
                assert gl.glGetError() == gl.GL_NO_ERROR
            alpha_results.append({"shader": shader_name, "transparent_partial_opaque_alpha": "pass"})
            gl.glDeleteTextures(1, [overlay_texture])
            gl.glDeleteProgram(overlay_program)
        return {"backend": "host Mesa EGL / GLES2 (not Android)", "glsl_source": "WorkspaceEffectGlEffect.kt", "width": width, "height": height, "presets": result, "overlay_alpha": alpha_results, "distinct_color_looks": len(filter_outputs), "custom_color_controls": custom_checks}

    finally:
        egl.eglMakeCurrent(display, egl.EGL_NO_SURFACE, egl.EGL_NO_SURFACE, egl.EGL_NO_CONTEXT)
        egl.eglDestroyContext(display, context)
        egl.eglDestroySurface(display, surface)
        egl.eglTerminate(display)


if __name__ == "__main__":
    print(json.dumps(run(), indent=2))
