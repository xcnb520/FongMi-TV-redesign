package com.fongmi.player.core;

public final class TrackInfo {

    private final TrackType type;
    private final int index;
    private final String mime;
    private final String codec;
    private final String language;
    private final int width;
    private final int height;
    private final float frameRate;
    private final int bitrate;
    private final int channels;
    private final int sampleRate;
    private boolean selected;

    public TrackInfo(TrackType type, int index, String mime, String codec, String language, int width, int height, float frameRate, int bitrate, int channels, int sampleRate, boolean selected) {
        this.type = type;
        this.index = index;
        this.mime = mime;
        this.codec = codec;
        this.language = language;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.bitrate = bitrate;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.selected = selected;
    }

    public TrackType getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public String getMime() {
        return mime;
    }

    public String getCodec() {
        return codec;
    }

    public String getLanguage() {
        return language;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
