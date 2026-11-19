/*******************************************************************************
 * Copyright 2024 See AUTHORS file.
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

package arc.video.assets;

import arc.assets.AssetDescriptor;
import arc.assets.AssetLoaderParameters;
import arc.assets.AssetManager;
import arc.assets.loaders.AsynchronousAssetLoader;
import arc.assets.loaders.FileHandleResolver;
import arc.files.Fi;
import arc.graphics.Texture.TextureFilter;
import arc.struct.Seq;
import arc.util.Log;
import arc.video.VideoPlayer;
import arc.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

public class VideoLoader extends AsynchronousAssetLoader<VideoPlayer, VideoLoader.VideoParameter> {

	public VideoLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	private VideoPlayer videoPlayer;

	@Override
	public void loadAsync (AssetManager manager, String fileName, Fi file, VideoParameter parameter) {
		videoPlayer = null;
		videoPlayer = VideoPlayerCreator.createVideoPlayer();
		videoPlayer.setFilter(parameter.minFilter, parameter.magFilter);
		videoPlayer.setLooping(parameter.looping);
		videoPlayer.setVolume(parameter.volume);
		try {
			videoPlayer.load(file);
		} catch (FileNotFoundException ignored) {
			Log.err("gdx-video", "Video file " + fileName + " not found");
		}
	}

	@Override
	public VideoPlayer loadSync (AssetManager manager, String fileName, Fi file, VideoParameter parameter) {
		VideoPlayer player = this.videoPlayer;
		this.videoPlayer = null;
		return player;
	}

	@Override
	public Seq<AssetDescriptor> getDependencies (String fileName, Fi file, VideoParameter parameter) {
		return null;
	}

	public static class VideoParameter extends AssetLoaderParameters<VideoPlayer> {
		public TextureFilter minFilter, magFilter;
		public boolean looping;
		public float volume = 1;
	}

}
