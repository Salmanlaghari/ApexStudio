#!/usr/bin/env python3
"""Compile/link the production adjustment GLSL on host Mesa GLES2; not Android/device acceptance."""
from __future__ import annotations

import json
import os
import re
import textwrap
from pathlib import Path

os.environ.setdefault("PYOPENGL_PLATFORM", "egl")
os.environ.setdefault("EGL_PLATFORM", "surfaceless")

from OpenGL import EGL, GLES2 as gl  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/apexstudio/app/data/adjust/AdjustmentsGlEffect.kt"
UNIFORMS = (
    "uHslRed",
    "uHslYellow",
    "uHslGreen",
    "uHslCyan",
    "uHslBlue",
    "uHslMagenta",
)


def shader_source(name: str) -> str:
    source = SOURCE.read_text(encoding="utf-8")
    match = re.search(
        rf'private val {name}\s*=\s*"""(.*?)"""\.trimIndent\(\)',
        source,
        re.DOTALL,
    )
    if not match:
        raise RuntimeError(f"Could not find the {name} GLSL raw string in {SOURCE}")
    return textwrap.dedent(match.group(1)).strip()


def compile_shader(kind: int, source: str, label: str) -> int:
    shader = gl.glCreateShader(kind)
    gl.glShaderSource(shader, source)
    gl.glCompileShader(shader)
    if not gl.glGetShaderiv(shader, gl.GL_COMPILE_STATUS):
        log = gl.glGetShaderInfoLog(shader)
        gl.glDeleteShader(shader)
        raise RuntimeError(f"{label} shader compilation failed:\n{log.decode(errors='replace')}")
    return shader


def main() -> None:
    display = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
    major, minor = EGL.EGLint(), EGL.EGLint()
    if not EGL.eglInitialize(display, major, minor):
        raise RuntimeError("Could not initialize host EGL")
    surface = context = EGL.EGL_NO_SURFACE
    program = 0
    shaders: list[int] = []
    try:
        config_attributes = (EGL.EGLint * 15)(
            EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT,
            EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT,
            EGL.EGL_RED_SIZE, 8,
            EGL.EGL_GREEN_SIZE, 8,
            EGL.EGL_BLUE_SIZE, 8,
            EGL.EGL_ALPHA_SIZE, 8,
            EGL.EGL_NONE, 0, 0,
        )
        configs, count = (EGL.EGLConfig * 1)(), EGL.EGLint()
        if not EGL.eglChooseConfig(display, config_attributes, configs, 1, count) or count.value == 0:
            raise RuntimeError("No host EGL GLES2 pbuffer config is available")
        EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
        surface_attributes = (EGL.EGLint * 5)(EGL.EGL_WIDTH, 96, EGL.EGL_HEIGHT, 64, EGL.EGL_NONE)
        surface = EGL.eglCreatePbufferSurface(display, configs[0], surface_attributes)
        context_attributes = (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE)
        context = EGL.eglCreateContext(display, configs[0], EGL.EGL_NO_CONTEXT, context_attributes)
        if surface == EGL.EGL_NO_SURFACE or context == EGL.EGL_NO_CONTEXT:
            raise RuntimeError("Could not create host GLES2 context/surface")
        if not EGL.eglMakeCurrent(display, surface, surface, context):
            raise RuntimeError("Could not make host GLES2 context current")

        shaders.append(compile_shader(gl.GL_VERTEX_SHADER, shader_source("VERTEX_SHADER"), "vertex"))
        shaders.append(compile_shader(gl.GL_FRAGMENT_SHADER, shader_source("FRAGMENT_SHADER"), "fragment"))
        program = gl.glCreateProgram()
        for shader in shaders:
            gl.glAttachShader(program, shader)
        gl.glLinkProgram(program)
        if not gl.glGetProgramiv(program, gl.GL_LINK_STATUS):
            log = gl.glGetProgramInfoLog(program)
            raise RuntimeError(f"Adjustment shader link failed:\n{log.decode(errors='replace')}")
        missing = [name for name in UNIFORMS if gl.glGetUniformLocation(program, name) < 0]
        if missing:
            raise RuntimeError(f"HSL uniforms were optimized out or missing: {missing}")
        gl_info = {
            "backend": "host Mesa EGL / GLES2 (not Android/device)",
            "vertex_compiled": True,
            "fragment_compiled": True,
            "program_linked": True,
            "active_hsl_uniforms": list(UNIFORMS),
        }
        print(json.dumps(gl_info, indent=2))
    finally:
        if program:
            gl.glDeleteProgram(program)
        for shader in shaders:
            gl.glDeleteShader(shader)
        EGL.eglMakeCurrent(display, EGL.EGL_NO_SURFACE, EGL.EGL_NO_SURFACE, EGL.EGL_NO_CONTEXT)
        if surface != EGL.EGL_NO_SURFACE:
            EGL.eglDestroySurface(display, surface)
        if context != EGL.EGL_NO_CONTEXT:
            EGL.eglDestroyContext(display, context)
        EGL.eglTerminate(display)


if __name__ == "__main__":
    main()
