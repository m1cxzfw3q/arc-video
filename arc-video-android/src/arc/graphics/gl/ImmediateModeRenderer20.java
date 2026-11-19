/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package arc.graphics.gl;

import arc.video.glext.ImmediateModeRenderer;
import arc.graphics.Color;
import arc.graphics.Mesh;
import arc.graphics.VertexAttribute;
import arc.math.Mat;
import arc.struct.Seq;
import arc.util.ArcRuntimeException;

/** Immediate mode rendering class for GLES 2.0. The renderer will allow you to specify vertices on the fly and provides a default
 * shader for (unlit) rendering.
 * @author mzechner */
public class ImmediateModeRenderer20 implements ImmediateModeRenderer {
	private int primitiveType;
	private int vertexIdx;
	private int numSetTexCoords;
	private final int maxVertices;
	private int numVertices;

	private final Mesh mesh;
	private Shader shader;
	private boolean ownsShader;
	private final int numTexCoords;
	private final int vertexSize;
	private final Mat projModelView = new Mat();
	private final float[] vertices;
	private final String[] shaderUniformNames;

	public ImmediateModeRenderer20 (boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(5000, hasNormals, hasColors, numTexCoords, createDefaultShader(hasNormals, hasColors, numTexCoords));
		ownsShader = true;
	}

	public ImmediateModeRenderer20 (int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(maxVertices, hasNormals, hasColors, numTexCoords, createDefaultShader(hasNormals, hasColors, numTexCoords));
		ownsShader = true;
	}

	public ImmediateModeRenderer20 (int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords, Shader shader) {
		this.maxVertices = maxVertices;
		this.numTexCoords = numTexCoords;
		this.shader = shader;

		VertexAttribute[] attribs = buildVertexAttributes(hasNormals, hasColors, numTexCoords);
		mesh = new Mesh(false, maxVertices, 0, attribs);

		vertices = new float[maxVertices * (mesh.vertexSize / 4)];
		vertexSize = mesh.vertexSize / 4;

		shaderUniformNames = new String[numTexCoords];
		for (int i = 0; i < numTexCoords; i++) {
			shaderUniformNames[i] = "u_sampler" + i;
		}
	}

	private VertexAttribute[] buildVertexAttributes (boolean hasNormals, boolean hasColor, int numTexCoords) {
		Seq<VertexAttribute> attribs = new Seq<>();
		attribs.add(VertexAttribute.position);
		if (hasNormals) attribs.add(VertexAttribute.normal);
		if (hasColor) attribs.add(VertexAttribute.color);
		for (int i = 0; i < numTexCoords; i++) {
			attribs.add(VertexAttribute.texCoords);
		}
		VertexAttribute[] array = new VertexAttribute[attribs.size];
		for (int i = 0; i < attribs.size; i++)
			array[i] = attribs.get(i);
		return array;
	}

	public void setShader (Shader shader) {
		if (ownsShader) this.shader.dispose();
		this.shader = shader;
		ownsShader = false;
	}

	public Shader getShader () {
		return shader;
	}

	public void begin (Mat projModelView, int primitiveType) {
		this.projModelView.set(projModelView);
		this.primitiveType = primitiveType;
	}

	public void color (Color color) {
		vertices[vertexIdx] = color.toFloatBits();
	}

	public void color (float r, float g, float b, float a) {
		vertices[vertexIdx] = Color.toFloatBits(r, g, b, a);
	}

	public void color (float colorBits) {
		vertices[vertexIdx] = colorBits;
	}

	public void texCoord (float u, float v) {
		final int idx = vertexIdx;
		vertices[idx + numSetTexCoords] = u;
		vertices[idx + numSetTexCoords + 1] = v;
		numSetTexCoords += 2;
	}

	public void normal (float x, float y, float z) {
		final int idx = vertexIdx;
		vertices[idx] = x;
		vertices[idx + 1] = y;
		vertices[idx + 2] = z;
	}

	public void vertex (float x, float y, float z) {
		final int idx = vertexIdx;
		vertices[idx] = x;
		vertices[idx + 1] = y;
		vertices[idx + 2] = z;

		numSetTexCoords = 0;
		vertexIdx += vertexSize;
		numVertices++;
	}

	public void flush () {
		if (numVertices == 0) return;
		shader.bind();
		shader.setUniformMatrix("u_projModelView", projModelView);
		for (int i = 0; i < numTexCoords; i++)
			shader.setUniformi(shaderUniformNames[i], i);
		mesh.setVertices(vertices, 0, vertexIdx);
		mesh.render(shader, primitiveType);

		numSetTexCoords = 0;
		vertexIdx = 0;
		numVertices = 0;
	}

	public void end () {
		flush();
	}

	public int getNumVertices () {
		return numVertices;
	}

	@Override
	public int getMaxVertices () {
		return maxVertices;
	}

	public void dispose () {
		if (ownsShader && shader != null) shader.dispose();
		mesh.dispose();
	}

	static private String createVertexShader (boolean hasNormals, boolean hasColors, int numTexCoords) {
		StringBuilder shader = new StringBuilder("attribute vec4 " + Shader.positionAttribute + ";\n"
                + (hasNormals ? "attribute vec3 " + Shader.normalAttribute + ";\n" : "")
                + (hasColors ? "attribute vec4 " + Shader.colorAttribute + ";\n" : ""));

		for (int i = 0; i < numTexCoords; i++) {
			shader.append("attribute vec2 " + Shader.texcoordAttribute).append(i).append(";\n");
		}

		shader.append("uniform mat4 u_projModelView;\n" //
        ).append(hasColors ? "varying vec4 v_col;\n" : "");

		for (int i = 0; i < numTexCoords; i++) {
			shader.append("varying vec2 v_tex").append(i).append(";\n");
		}

		shader.append("void main() {\n" + "   gl_Position = u_projModelView * " + Shader.positionAttribute + ";\n");
		if (hasColors) {
			shader.append("   v_col = " + Shader.colorAttribute + ";\n" //
                    + "   v_col.a *= 255.0 / 254.0;\n");
		}

		for (int i = 0; i < numTexCoords; i++) {
			shader.append("   v_tex").append(i).append(" = ").append(Shader.texcoordAttribute).append(i).append(";\n");
		}
		shader.append("   gl_PointSize = 1.0;\n" //
                + "}\n");
		return shader.toString();
	}

	static private String createFragmentShader (boolean hasNormals, boolean hasColors, int numTexCoords) {
		StringBuilder shader = new StringBuilder("#ifdef GL_ES\n" + "precision mediump float;\n" + "#endif\n");

		if (hasColors) shader.append("varying vec4 v_col;\n");
		for (int i = 0; i < numTexCoords; i++) {
			shader.append("varying vec2 v_tex").append(i).append(";\n");
			shader.append("uniform sampler2D u_sampler").append(i).append(";\n");
		}

		shader.append("void main() {\n" //
                + "   gl_FragColor = ").append(hasColors ? "v_col" : "vec4(1, 1, 1, 1)");

		if (numTexCoords > 0) shader.append(" * ");

		for (int i = 0; i < numTexCoords; i++) {
			if (i == numTexCoords - 1) {
				shader.append(" texture2D(u_sampler").append(i).append(",  v_tex").append(i).append(")");
			} else {
				shader.append(" texture2D(u_sampler").append(i).append(",  v_tex").append(i).append(") *");
			}
		}

		shader.append(";\n}");
		return shader.toString();
	}

	/** Returns a new instance of the default shader used by SpriteBatch for GL2 when no shader is specified. */
	static public Shader createDefaultShader (boolean hasNormals, boolean hasColors, int numTexCoords) {
		String vertexShader = createVertexShader(hasNormals, hasColors, numTexCoords);
		String fragmentShader = createFragmentShader(hasNormals, hasColors, numTexCoords);
		Shader program = new Shader(vertexShader, fragmentShader);
		if (!program.isCompiled()) throw new ArcRuntimeException("Error compiling shader: " + program.getLog());
		return program;
	}
}
