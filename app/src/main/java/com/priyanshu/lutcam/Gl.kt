package com.priyanshu.lutcam

import android.opengl.GLES20 as G

object Gl {
    fun program(vs: String, fs: String): Int {
        val v = compile(G.GL_VERTEX_SHADER, vs)
        val f = compile(G.GL_FRAGMENT_SHADER, fs)
        val p = G.glCreateProgram()
        G.glAttachShader(p, v)
        G.glAttachShader(p, f)
        G.glLinkProgram(p)
        val st = IntArray(1)
        G.glGetProgramiv(p, G.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) {
            val log = G.glGetProgramInfoLog(p)
            G.glDeleteProgram(p)
            throw RuntimeException("Shader link error: $log")
        }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = G.glCreateShader(type)
        G.glShaderSource(s, src)
        G.glCompileShader(s)
        val st = IntArray(1)
        G.glGetShaderiv(s, G.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) {
            val log = G.glGetShaderInfoLog(s)
            G.glDeleteShader(s)
            throw RuntimeException("Shader compile error: $log")
        }
        return s
    }

    fun texParams(target: Int) {
        G.glTexParameteri(target, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(target, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(target, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(target, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
    }

    /** Returns [framebuffer, texture]. */
    fun makeFbo(w: Int, h: Int): IntArray {
        val t = IntArray(1)
        G.glGenTextures(1, t, 0)
        G.glBindTexture(G.GL_TEXTURE_2D, t[0])
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, w, h, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
        texParams(G.GL_TEXTURE_2D)
        val f = IntArray(1)
        G.glGenFramebuffers(1, f, 0)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, f[0])
        G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, t[0], 0)
        val status = G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        if (status != G.GL_FRAMEBUFFER_COMPLETE) {
            G.glDeleteFramebuffers(1, f, 0)
            G.glDeleteTextures(1, t, 0)
            throw RuntimeException("Framebuffer error $status")
        }
        return intArrayOf(f[0], t[0])
    }
}
