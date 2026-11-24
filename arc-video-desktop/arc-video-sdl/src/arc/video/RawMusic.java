/*******************************************************************************
 * Copyright 2014 See AUTHORS file.
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

package arc.video;

import java.nio.ByteBuffer;

import arc.audio.Music;
import arc.util.Log;
import net.sourceforge.lame.lowlevel.LameEncoder;

import javax.sound.sampled.AudioFormat;

import static net.sourceforge.lame.lowlevel.LameEncoder.*;
import static net.sourceforge.lame.mp3.MPEGMode.JOINT_STEREO;

/** The RawMusic class extends OpenAlMusic, and retrieves it's audio from a VideoDecoder instance.
 *
 * @author Rob Bogie <rob.bogie@codepoke.net> */
class RawMusic extends Music {
	VideoDecoder decoder;
	ByteBuffer backBuffer;
	private byte[] tmpBytes;

    public RawMusic (VideoDecoder decoder, ByteBuffer buffer, int channels, int sampleRate) {
		this.decoder = decoder;
		backBuffer = buffer;
		backBuffer.position(backBuffer.limit());

		LameEncoder encoder = new LameEncoder(
				new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, 2, sampleRate, false),
				BITRATE_AUTO, JOINT_STEREO, DEFAULT_QUALITY, DEFAULT_VBR
		);

		byte[] encoded = new byte[backBuffer.limit()], tmpBytes = new byte[backBuffer.limit()];
		backBuffer.get(tmpBytes);
		encoder.encodeBuffer(tmpBytes, 0, backBuffer.limit(), encoded);

        try {
            load(encoded);
        } catch (Exception e) {
			Log.err("Failed loading music from byte buffer", e);
        }
    }
}
