#!/usr/bin/env python3
"""Render production keyframe GLSL on host Mesa EGL; NOT an Android/device test."""
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
    source = (root / "app/src/main/java/com/apexstudio/app/data/animation/KeyframeAnimationEffect.kt").read_text()
    def shader(name):
        match = re.search(r'(?:private )?val ' + name + r' = """(.*?)"""', source, re.S)
        assert match, f"Production shader {name} not found"
        return textwrap.dedent(match.group(1)).strip()
    display = egl.eglGetDisplay(egl.EGL_DEFAULT_DISPLAY)
    major, minor = egl.EGLint(), egl.EGLint()
    assert egl.eglInitialize(display, major, minor)
    attrs = (egl.EGLint * 13)(egl.EGL_SURFACE_TYPE, egl.EGL_PBUFFER_BIT,
        egl.EGL_RENDERABLE_TYPE, egl.EGL_OPENGL_ES2_BIT, egl.EGL_RED_SIZE, 8,
        egl.EGL_GREEN_SIZE, 8, egl.EGL_BLUE_SIZE, 8, egl.EGL_ALPHA_SIZE, 8, egl.EGL_NONE)
    configs, count = (egl.EGLConfig * 1)(), egl.EGLint()
    assert egl.eglChooseConfig(display, attrs, configs, 1, count) and count.value
    width, height = 96, 64
    surface_attrs = (egl.EGLint * 5)(egl.EGL_WIDTH, width, egl.EGL_HEIGHT, height, egl.EGL_NONE)
    surface = egl.eglCreatePbufferSurface(display, configs[0], surface_attrs)
    context_attrs = (egl.EGLint * 3)(egl.EGL_CONTEXT_CLIENT_VERSION, 2, egl.EGL_NONE)
    egl.eglBindAPI(egl.EGL_OPENGL_ES_API)
    context = egl.eglCreateContext(display, configs[0], egl.EGL_NO_CONTEXT, context_attrs)
    assert egl.eglMakeCurrent(display, surface, surface, context)
    program = 0
    texture = None
    try:
        def compile_shader(kind, text):
            handle = gl.glCreateShader(kind)
            gl.glShaderSource(handle, text)
            gl.glCompileShader(handle)
            assert gl.glGetShaderiv(handle, gl.GL_COMPILE_STATUS), gl.glGetShaderInfoLog(handle)
            return handle
        vertex = compile_shader(gl.GL_VERTEX_SHADER, shader("VERTEX_SHADER"))
        fragment = compile_shader(gl.GL_FRAGMENT_SHADER, shader("FRAGMENT_SHADER"))
        program = gl.glCreateProgram()
        gl.glAttachShader(program, vertex)
        gl.glAttachShader(program, fragment)
        gl.glLinkProgram(program)
        assert gl.glGetProgramiv(program, gl.GL_LINK_STATUS), gl.glGetProgramInfoLog(program)
        gl.glDeleteShader(vertex)
        gl.glDeleteShader(fragment)
        gl.glUseProgram(program)
        positions = np.array([[-1,-1,0,1],[1,-1,0,1],[-1,1,0,1],[1,1,0,1]], dtype=np.float32)
        attr = gl.glGetAttribLocation(program, "aFramePosition")
        gl.glEnableVertexAttribArray(attr)
        gl.glVertexAttribPointer(attr, 4, gl.GL_FLOAT, False, 0, positions)
        texture = gl.glGenTextures(1)
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, texture)
        for key in (gl.GL_TEXTURE_MIN_FILTER, gl.GL_TEXTURE_MAG_FILTER):
            gl.glTexParameteri(gl.GL_TEXTURE_2D, key, gl.GL_NEAREST)
        pixels = np.full((height, width, 4), [200, 100, 50, 255], dtype=np.uint8)
        gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, width, height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, pixels)
        gl.glUniform1i(gl.glGetUniformLocation(program, "uTexSampler"), 0)
        tex_matrix = np.array([[.5,0,0,.5],[0,.5,0,.5],[0,0,1,0],[0,0,0,1]], dtype=np.float32)
        def matrix(name, value):
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(program, name), 1, False,
                np.asarray(value, dtype=np.float32).flatten(order="F"))
        matrix("uTexTransformationMatrix", tex_matrix)
        def render(opacity=1.0, saturation=1.0, transform=None):
            gl.glViewport(0, 0, width, height)
            gl.glDisable(gl.GL_BLEND)
            gl.glClearColor(0, 0, 0, 1)
            gl.glClear(gl.GL_COLOR_BUFFER_BIT)
            matrix("uTransformationMatrix", np.eye(4) if transform is None else transform)
            weights = np.array([.2126, .7152, .0722])
            sat = np.eye(4)
            sat[:3,:3] = (1-saturation) * np.tile(weights, (3,1)) + saturation * np.eye(3)
            matrix("uSaturationMatrix", sat)
            gl.glUniform1f(gl.glGetUniformLocation(program, "uOpacity"), opacity)
            gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
            gl.glFinish()
            out = np.empty_like(pixels)
            gl.glReadPixels(0, 0, width, height, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, out)
            assert gl.glGetError() == gl.GL_NO_ERROR
            return out
        identity = render()
        assert np.max(np.abs(identity.astype(int)-pixels.astype(int))) <= 1, "Identity changed actual pixels"
        half = render(opacity=.5)
        assert np.max(np.abs(half[height//2,width//2,:3].astype(int)-np.array([100,50,25]))) <= 2, "Opacity did not dim encoded RGB"
        assert not render(opacity=0)[:,:,:3].any(), "Zero opacity retained source footage"
        gray = render(saturation=0)
        assert np.ptp(gray[height//2,width//2,:3].astype(int)) <= 1, "Saturation pins did not render grayscale"
        small = np.diag([.5,.5,1,1])
        scaled = render(transform=small)
        assert not scaled[0,0,:3].any() and scaled[height//2,width//2,0] > 190, "Scale/black frame bounds incorrect"
        translated = np.eye(4)
        translated[0,3] = .5
        shifted = render(transform=translated)
        assert not shifted[height//2,0,:3].any() and shifted[height//2,width//2,0] > 190, "Translation did not move source pixels"
        print(json.dumps({"environment":"host Mesa GLES, not Android", "production_shaders_compile":True,
            "checks":["identity", "half-opacity encoded RGB", "zero opacity", "saturation grayscale", "scale bounds", "translated bounds"]}, indent=2))
    finally:
        if texture is not None:
            gl.glDeleteTextures(1, [texture])
        if program:
            gl.glDeleteProgram(program)
        egl.eglMakeCurrent(display, egl.EGL_NO_SURFACE, egl.EGL_NO_SURFACE, egl.EGL_NO_CONTEXT)
        egl.eglDestroyContext(display, context)
        egl.eglDestroySurface(display, surface)
        egl.eglTerminate(display)


if __name__ == "__main__":
    run()
