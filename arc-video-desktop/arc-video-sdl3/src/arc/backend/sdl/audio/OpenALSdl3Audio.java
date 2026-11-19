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

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTDisconnect.ALC_CONNECTED;
import static org.lwjgl.openal.EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

import arc.audio.AudioDevice;
import arc.audio.AudioRecorder;
import arc.files.Fi;
import arc.math.Mathf;
import arc.util.ArcRuntimeException;
import arc.struct.Seq;
import arc.struct.IntSeq;
import arc.struct.IntMap;
import arc.struct.LongMap;
import arc.struct.ObjectMap;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.SOFTDirectChannels;
import org.lwjgl.openal.SOFTReopenDevice;
import org.lwjgl.openal.SOFTXHoldOnDisconnect;
import org.lwjgl.openal.SOFTDirectChannelsRemix;

/** @author Nathan Sweet */
public class OpenALSdl3Audio extends Sdl3Audio {
	private final int deviceBufferSize;
	private final int deviceBufferCount;
	private IntSeq idleSources, allSources;
	private LongMap<Integer> soundIdToSource;
	private IntMap<Long> sourceToSoundId;
	private long nextSoundId = 0;
	private ObjectMap<String, BiFunction<OpenALSdl3Audio, Fi, OpenALMusic>> extensionToMusicClass = new ObjectMap<>();
	private int mostRecetSound = -1;
	private String preferredOutputDevice = null;
	private Thread observerThread;

	Seq<OpenALMusic> music = new Seq<>(false, 1);
	long device;
	long context;
	boolean noDevice = false;

	public OpenALSdl3Audio() {
		this(16, 9, 512);
	}

	public OpenALSdl3Audio(int simultaneousSources, int deviceBufferCount, int deviceBufferSize) {
		this.deviceBufferSize = deviceBufferSize;
		this.deviceBufferCount = deviceBufferCount;

		registerMusic("ogg", OggMusic::new);
		registerMusic("wav", WavMusic::new);
		registerMusic("mp3", Mp3Music::new);

		device = alcOpenDevice((ByteBuffer)null);
		if (device == 0L) {
			noDevice = true;
			return;
		}
		ALCCapabilities deviceCapabilities = ALC.createCapabilities(device);
		context = alcCreateContext(device, (IntBuffer)null);
		if (context == 0L) {
			alcCloseDevice(device);
			noDevice = true;
			return;
		}
		if (!alcMakeContextCurrent(context)) {
			noDevice = true;
			return;
		}
		AL.createCapabilities(deviceCapabilities);

		alGetError();
		allSources = new IntSeq(false, simultaneousSources);
		for (int i = 0; i < simultaneousSources; i++) {
			int sourceID = alGenSources();
			if (alGetError() != AL_NO_ERROR) break;
			allSources.add(sourceID);
		}
		idleSources = new IntSeq(allSources);
		soundIdToSource = new LongMap<>();
		sourceToSoundId = new IntMap<>();

		FloatBuffer orientation = BufferUtils.createFloatBuffer(6).put(new float[] {0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f});
		((Buffer)orientation).flip();
		alListenerfv(AL_ORIENTATION, orientation);
		FloatBuffer velocity = BufferUtils.createFloatBuffer(3).put(new float[] {0.0f, 0.0f, 0.0f});
		((Buffer)velocity).flip();
		alListenerfv(AL_VELOCITY, velocity);
		FloatBuffer position = BufferUtils.createFloatBuffer(3).put(new float[] {0.0f, 0.0f, 0.0f});
		((Buffer)position).flip();
		alListenerfv(AL_POSITION, position);

		alDisable(SOFTXHoldOnDisconnect.AL_STOP_SOURCES_ON_DISCONNECT_SOFT);
		observerThread = new Thread(new Runnable() {

			private String[] lastAvailableDevices = new String[0];

			@Override
			public void run () {
				while (true) {
					boolean isConnected = alcGetInteger(device, ALC_CONNECTED) != 0;
					if (!isConnected) {
						// The device is at a state where it can't recover
						// This is usually the windows path on removing a device
						switchOutputDevice(null, false);
						continue;
					}
					if (preferredOutputDevice != null) {
						if (Arrays.asList(getAvailableOutputDevices()).contains(preferredOutputDevice)) {
							if (!preferredOutputDevice.equals(alcGetString(device, ALC_ALL_DEVICES_SPECIFIER))) {
								// The preferred output device is reconnected, let's switch back to it
								switchOutputDevice(preferredOutputDevice);
							}
						} else {
							// This is usually the mac/linux path
							if (preferredOutputDevice.equals(alcGetString(device, ALC_ALL_DEVICES_SPECIFIER))) {
								// The preferred output device is reconnected, let's switch back to it
								switchOutputDevice(null, false);
							}
						}
					} else {
						String[] currentDevices = getAvailableOutputDevices();
						// If a new device got added, re evaluate "auto" mode
						if (!Arrays.equals(currentDevices, lastAvailableDevices)) {
							switchOutputDevice(null);
						}
						// Update last available devices
						lastAvailableDevices = currentDevices;
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
						return;
					}
				}
			}
		});
		observerThread.setDaemon(true);
		observerThread.start();
	}

	public void registerMusic (String extension, BiFunction<OpenALSdl3Audio, Fi, OpenALMusic> soundSupplier) {
		if (extension == null) throw new IllegalArgumentException("extension cannot be null.");
		if (soundSupplier == null) throw new IllegalArgumentException("musicClass cannot be null.");
		extensionToMusicClass.put(extension, soundSupplier);
	}

	public OpenALMusic newMusic (Fi file) {
		if (file == null) throw new IllegalArgumentException("file cannot be null.");
		BiFunction<OpenALSdl3Audio, Fi, OpenALMusic> musicSupplier = extensionToMusicClass.get(file.extension().toLowerCase());
		if (musicSupplier == null) throw new ArcRuntimeException("Unknown file extension for music: " + file);
		return musicSupplier.apply(this, file);
	}

	public boolean switchOutputDevice (String deviceIdentifier) {
		return switchOutputDevice(deviceIdentifier, true);
	}

	private boolean switchOutputDevice (String deviceIdentifier, boolean setPreferred) {
		if (setPreferred) {
			preferredOutputDevice = deviceIdentifier;
		}
		return SOFTReopenDevice.alcReopenDeviceSOFT(device, deviceIdentifier, (IntBuffer)null);
	}

	public String[] getAvailableOutputDevices () {
		List<String> devices = ALUtil.getStringList(0, ALC_ALL_DEVICES_SPECIFIER);
		if (devices == null) return new String[0];
		return devices.toArray(new String[0]);
	}

	int obtainSource (boolean isMusic) {
		if (noDevice) return 0;
		for (int i = 0, n = idleSources.size; i < n; i++) {
			int sourceId = idleSources.get(i);
			int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
			if (state != AL_PLAYING && state != AL_PAUSED) {
				Long oldSoundId = sourceToSoundId.remove(sourceId);
				if (oldSoundId != null) soundIdToSource.remove(oldSoundId);
				if (isMusic) {
					idleSources.removeIndex(i);
				} else {
					long soundId = nextSoundId++;
					sourceToSoundId.put(sourceId, soundId);
					soundIdToSource.put(soundId, sourceId);
				}
				alSourceStop(sourceId);
				alSourcei(sourceId, AL_BUFFER, 0);
				AL10.alSourcef(sourceId, AL10.AL_GAIN, 1);
				AL10.alSourcef(sourceId, AL10.AL_PITCH, 1);
				AL10.alSource3f(sourceId, AL10.AL_POSITION, 0, 0, 1f);
				AL10.alSourcei(sourceId, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, SOFTDirectChannelsRemix.AL_REMIX_UNMATCHED_SOFT);
				return sourceId;
			}
		}
		return -1;
	}

	void freeSource (int sourceID) {
		if (noDevice) return;
		alSourceStop(sourceID);
		alSourcei(sourceID, AL_BUFFER, 0);
		Long soundId = sourceToSoundId.remove(sourceID);
		if (soundId != null) soundIdToSource.remove(soundId);
		idleSources.add(sourceID);
	}

	void freeBuffer (int bufferID) {
		if (noDevice) return;
		for (int i = 0, n = idleSources.size; i < n; i++) {
			int sourceID = idleSources.get(i);
			if (alGetSourcei(sourceID, AL_BUFFER) == bufferID) {
				Long soundId = sourceToSoundId.remove(sourceID);
				if (soundId != null) soundIdToSource.remove(soundId);
				alSourceStop(sourceID);
				alSourcei(sourceID, AL_BUFFER, 0);
			}
		}
	}

	void stopSourcesWithBuffer (int bufferID) {
		if (noDevice) return;
		for (int i = 0, n = idleSources.size; i < n; i++) {
			int sourceID = idleSources.get(i);
			if (alGetSourcei(sourceID, AL_BUFFER) == bufferID) {
				Long soundId = sourceToSoundId.remove(sourceID);
				if (soundId != null) soundIdToSource.remove(soundId);
				alSourceStop(sourceID);
			}
		}
	}

	void pauseSourcesWithBuffer (int bufferID) {
		if (noDevice) return;
		for (int i = 0, n = idleSources.size; i < n; i++) {
			int sourceID = idleSources.get(i);
			if (alGetSourcei(sourceID, AL_BUFFER) == bufferID) alSourcePause(sourceID);
		}
	}

	void resumeSourcesWithBuffer (int bufferID) {
		if (noDevice) return;
		for (int i = 0, n = idleSources.size; i < n; i++) {
			int sourceID = idleSources.get(i);
			if (alGetSourcei(sourceID, AL_BUFFER) == bufferID) {
				if (alGetSourcei(sourceID, AL_SOURCE_STATE) == AL_PAUSED) alSourcePlay(sourceID);
			}
		}
	}

	@Override
	public void update () {
		if (noDevice) return;
		for (int i = 0; i < music.size; i++)
			music.items[i].update();
	}

	public long getSoundId (int sourceId) {
		Long soundId = sourceToSoundId.get(sourceId);
		return soundId != null ? soundId : -1;
	}

	public int getSourceId (long soundId) {
		Integer sourceId = soundIdToSource.get(soundId);
		return sourceId != null ? sourceId : -1;
	}

	public void stopSound (long soundId) {
		Integer sourceId = soundIdToSource.get(soundId);
		if (sourceId != null) alSourceStop(sourceId);
	}

	public void pauseSound (long soundId) {
		Integer sourceId = soundIdToSource.get(soundId);
		if (sourceId != null) alSourcePause(sourceId);
	}

	public void resumeSound (long soundId) {
		int sourceId = soundIdToSource.get(soundId, -1);
		if (sourceId != -1 && alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PAUSED) alSourcePlay(sourceId);
	}

	public void setSoundGain (long soundId, float volume) {
		Integer sourceId = soundIdToSource.get(soundId);
		if (sourceId != null) AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
	}

	public void setSoundLooping (long soundId, boolean looping) {
		Integer sourceId = soundIdToSource.get(soundId);
		if (sourceId != null) alSourcei(sourceId, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
	}

	public void setSoundPitch (long soundId, float pitch) {
		Integer sourceId = soundIdToSource.get(soundId);
		if (sourceId != null) AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch);
	}

	public void setSoundPan (long soundId, float pan, float volume) {
		int sourceId = soundIdToSource.get(soundId, -1);
		if (sourceId != -1) {
			AL10.alSource3f(sourceId, AL10.AL_POSITION, Mathf.cos((pan - 1) * Mathf.halfPi), 0,
				Mathf.sin((pan + 1) * Mathf.halfPi));
			AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
		}
	}

	@Override
	public void dispose () {
		if (noDevice) return;
		observerThread.interrupt();
		for (int i = 0, n = allSources.size; i < n; i++) {
			int sourceID = allSources.get(i);
			int state = alGetSourcei(sourceID, AL_SOURCE_STATE);
			if (state != AL_STOPPED) alSourceStop(sourceID);
			alDeleteSources(sourceID);
		}

		sourceToSoundId = null;
		soundIdToSource = null;

		alcDestroyContext(context);
		alcCloseDevice(device);
	}

	public AudioDevice newAudioDevice (int sampleRate, final boolean isMono) {
		if (noDevice) return new AudioDevice() {
			@Override
			public void writeSamples (float[] samples, int offset, int numSamples) {
			}

			@Override
			public void writeSamples (short[] samples, int offset, int numSamples) {
			}

			@Override
			public void setVolume (float volume) {
			}

			@Override
			public boolean isMono () {
				return isMono;
			}

			@Override
			public int getLatency () {
				return 0;
			}

			@Override
			public void dispose () {
			}

			@Override
			public void pause () {
			}

			@Override
			public void resume () {
			}
		};
		return new OpenALAudioDevice(this, sampleRate, isMono, deviceBufferSize, deviceBufferCount);
	}

	public AudioRecorder newAudioRecorder (int samplingRate, boolean isMono) {
		if (noDevice) return new AudioRecorder() {
			@Override
			public void read (short[] samples, int offset, int numSamples) {
			}

			@Override
			public void dispose () {
			}
		};
		return new JavaSoundAudioRecorder(samplingRate, isMono);
	}
}
