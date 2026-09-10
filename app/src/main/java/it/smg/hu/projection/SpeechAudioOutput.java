package it.smg.hu.projection;

import android.media.AudioManager;

import it.smg.libs.aasdk.messenger.ChannelId;

public class SpeechAudioOutput extends AudioOutput {

    private static final String TAG = "SpeechAudioOutput";

    public SpeechAudioOutput(){
        super();

        channelConfig_ = settings_.audio.speechChannelCount();
        sampleSize_ = settings_.audio.speechSampleSize();
        sampleRate_ = settings_.audio.speechSampleRate();

        if (settings_.advanced.hondaIntegrationEnabled()){
            audioCodecStreamType_ = hondaConnectManager_.mediaAudioStream(ChannelId.SPEECH_AUDIO);
        } else {
            // SPEECH channel carries TTS/navigation/notifications (NOT call audio,
            // which is routed via Bluetooth HFP). Mix with media instead of
            // STREAM_VOICE_CALL to avoid conflicting with real calls.
            audioCodecStreamType_ = AudioManager.STREAM_MUSIC;
        }
    }

    @Override
    public String tag() {
        return TAG;
    }
}
