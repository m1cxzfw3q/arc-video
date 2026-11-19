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

package arc.backend.sdl.audio;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;

import arc.files.Fi;
import arc.util.ArcRuntimeException;
import arc.util.StreamUtils;

public class WavMusic extends OpenALMusic {
	private WavInputStream input;
	public WavMusic (OpenALSdl3Audio audio, Fi file) {
		super(audio, file);
		input = new WavInputStream(file);
		if (audio.noDevice) return;
		setup(input.channels, input.bitDepth, input.sampleRate);
	}

	public int read (byte[] buffer) {
		if (input == null) {
			input = new WavInputStream(file);
			setup(input.channels, input.bitDepth, input.sampleRate);
		}
		try {
			return input.read(buffer);
		} catch (IOException ex) {
			throw new ArcRuntimeException("Error reading WAV file: " + file, ex);
		}
	}

	public void reset () {
		StreamUtils.closeQuietly(input);
		input = null;
	}


	/** @author Nathan Sweet */
	static public class WavInputStream extends FilterInputStream {

		public int channels, bitDepth, sampleRate, dataRemaining, type;

		public WavInputStream (Fi file) {
			super(file.read());
			try {
				if (read() != 'R' || read() != 'I' || read() != 'F' || read() != 'F')
					throw new ArcRuntimeException("RIFF header not found: " + file);

				skipFully(4);

				if (read() != 'W' || read() != 'A' || read() != 'V' || read() != 'E')
					throw new ArcRuntimeException("Invalid wave file header: " + file);

				int fmtChunkLength = seekToChunk('f', 'm', 't', ' ');

				// http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
				// http://soundfile.sapp.org/doc/WaveFormat/
				type = read() & 0xff | (read() & 0xff) << 8;

				if (type == 0x0055) return; // Handle MP3 in constructor instead
				if (type != 0x0001 && type != 0x0003) throw new ArcRuntimeException(
					"WAV files must be PCM, unsupported format: " + getCodecName(type) + " (" + type + ")");

				channels = read() & 0xff | (read() & 0xff) << 8;
				sampleRate = read() & 0xff | (read() & 0xff) << 8 | (read() & 0xff) << 16 | (read() & 0xff) << 24;

				skipFully(6);

				bitDepth = read() & 0xff | (read() & 0xff) << 8;
				if (type == 0x0001) { // PCM
					if (bitDepth != 8 && bitDepth != 16)
						throw new ArcRuntimeException("PCM WAV files must be 8 or 16-bit: " + bitDepth);
				} else if (type == 0x0003) { // Float
					if (bitDepth != 32 && bitDepth != 64)
						throw new ArcRuntimeException("Floating-point WAV files must be 32 or 64-bit: " + bitDepth);
				}

				skipFully(fmtChunkLength - 16);

				dataRemaining = seekToChunk('d', 'a', 't', 'a');
			} catch (Throwable ex) {
				StreamUtils.closeQuietly(this);
				throw new ArcRuntimeException("Error reading WAV file: " + file, ex);
			}
		}

		private int seekToChunk (char c1, char c2, char c3, char c4) throws IOException {
			while (true) {
				boolean found = read() == c1;
				found &= read() == c2;
				found &= read() == c3;
				found &= read() == c4;
				int chunkLength = read() & 0xff | (read() & 0xff) << 8 | (read() & 0xff) << 16 | (read() & 0xff) << 24;
				if (chunkLength == -1) throw new IOException("Chunk not found: " + c1 + c2 + c3 + c4);
				if (found) return chunkLength;
				skipFully(chunkLength);
			}
		}

		private void skipFully (int count) throws IOException {
			while (count > 0) {
				long skipped = in.skip(count);
				if (skipped <= 0) throw new EOFException("Unable to skip.");
				count -= skipped;
			}
		}

		public int read (byte[] buffer) throws IOException {
			if (dataRemaining == 0) return -1;
			int offset = 0;
			do {
				int length = Math.min(super.read(buffer, offset, buffer.length - offset), dataRemaining);
				if (length == -1) {
					if (offset > 0) return offset;
					return -1;
				}
				offset += length;
				dataRemaining -= length;
			} while (offset < buffer.length);
			return offset;
		}

		/** List is a combination of Audacity's export formats and Windows ACM. For a more thorough list, see
		 * https://wiki.multimedia.cx/index.php/TwoCC
		 * @param type 16-bit value from the fmt chunk.
		 * @return A human-readable name for the codec. */
		private String getCodecName (int type) {
			switch (type) { // @off
				case 0x0002: return "Microsoft ADPCM";
				case 0x0006: return "ITU-T G.711 A-law";
				case 0x0007: return "ITU-T G.711 u-law";
				case 0x0011: return "IMA ADPCM";
				case 0x0022: return "DSP Group TrueSpeech";
				case 0x0031: return "Microsoft GSM 6.10";
				case 0x0040: return "Antex G.721 ADPCM";
				case 0x0070: return "Lernout & Hauspie CELP 4.8kbps";
				case 0x0072: return "Lernout & Hauspie CBS 12kbps";
				case 0xfffe: return "Extensible";
				default: return "Unknown"; // @on
			}
		}
	}
}
