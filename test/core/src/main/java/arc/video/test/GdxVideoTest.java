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

package arc.video.test;

import arc.Application;
import arc.ApplicationListener;
import arc.Core;
import arc.files.Fi;
import arc.graphics.GL20;
import arc.graphics.OrthographicCamera;
import arc.graphics.g2d.SpriteBatch;
import arc.video.VideoPlayer;
import arc.video.VideoPlayerCreator;
import arc.video.scene.VideoElement;

import java.io.FileNotFoundException;

/** {@link arc.ApplicationListener} implementation shared by all platforms. */
public class ArcVideoTest implements ApplicationListener {
	SpriteBatch batch;
	OrthographicCamera camera;
	VideoPlayer videoPlayer;
	VideoElement videoElement;

	protected Fi getVideoFile () {
		return Core.files.internal("libGDX - It's Good For You!.webm");
	}

	@Override
	public void create () {
		batch = new SpriteBatch();
		camera = new OrthographicCamera();
		videoPlayer = VideoPlayerCreator.createVideoPlayer();
		videoPlayer.setOnCompletionListener(new VideoPlayer.CompletionListener() {
			@Override
			public void onCompletionListener (Fi file) {
				Log.debug("VideoTest", file.name() + " fully played.");
			}
		});
		videoPlayer.setOnVideoSizeListener(new VideoPlayer.VideoSizeListener() {
			@Override
			public void onVideoSize (float width, float height) {
				Log.debug("VideoTest", "The video has a size of " + width + "x" + height + ".");
			}
		});

		videoElement = new VideoElement(videoPlayer);
		videoElement.setBounds(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
		try {
			videoPlayer.load(getVideoFile());
		} catch (FileNotFoundException e) {
			Log.err("arc-video", "Oh no!");
		}
	}

	@Override
	public void render () {
		if (Core.input.justTouched()) {
			if (videoPlayer.isPlaying()) {
				videoPlayer.pause();
			} else {
				videoPlayer.play();
			}
		}

		Core.gl.glClearColor(0, 0, 0, 0);
		Core.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		videoElement.act(Core.graphics.getDeltaTime());

		batch.begin();
		videoElement.draw(batch, 1f);
		batch.end();
	}

	@Override
	public void pause () {
		videoPlayer.pause();
	}

	@Override
	public void resume () {
		videoPlayer.play();
	}

	@Override
	public void dispose () {
		videoPlayer.dispose();
	}
}
