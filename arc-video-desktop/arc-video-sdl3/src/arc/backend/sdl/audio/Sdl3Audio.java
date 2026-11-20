package arc.backend.sdl.audio;

import arc.ApplicationListener;
import arc.Core;
import arc.audio.Audio;

public abstract class Sdl3Audio extends Audio {
    @Override
    public void initialize() {
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void update() {
                Sdl3Audio.this.update();
            }
        });
    }
    public void update() {

    }
}
