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

import arc.files.Fi;
import arc.util.StreamUtils;

/** @author Nathan Sweet */
public class OggMusic extends OpenALMusic {
	private OggInputStream input;
	private OggInputStream previousInput;

	public OggMusic (OpenALSdl3Audio audio, Fi file) {
		super(audio, file);
		if (audio.noDevice) return;
		input = new OggInputStream(file.read());
		setup(input.getChannels(), 16, input.getSampleRate());
	}

	public int read (byte[] buffer) {
		if (input == null) {
			input = new OggInputStream(file.read(), previousInput);
			setup(input.getChannels(), 16, input.getSampleRate());
			previousInput = null; // release this reference
		}
		return input.read(buffer);
	}

	public void reset () {
		StreamUtils.closeQuietly(input);
		previousInput = null;
		input = null;
	}

	@Override
	protected void loop () {
		StreamUtils.closeQuietly(input);
		previousInput = input;
		input = null;
	}
}

